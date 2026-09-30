/**
 * Image upload helpers.
 *
 * ---------------------------------------------------------------------------
 * The two ways to get a file into S3
 * ---------------------------------------------------------------------------
 *
 * 1. THROUGH THE API (what this module does by default)
 *    browser -> gateway -> product-service -> S3
 *    One simple call, and the service can validate, resize and reject the file.
 *    Cost: the bytes travel through your infrastructure twice and are held in
 *    the JVM's heap. Fine for avatars and product photos; painful at scale.
 *
 * 2. DIRECT TO S3 with a presigned URL (used when the backend says it's available)
 *    browser -> S3 directly
 *    The service only signs a short-lived URL. The upload bypasses the API
 *    entirely: faster, and your API never carries image bytes.
 *
 * `uploadViaPresignedUrl` implements path 2 and falls back to path 1, so the UI
 * does not care which backend is configured.
 */

import axios from 'axios';
import axiosInstance from './axiosInstance';

const MAX_BYTES = 5 * 1024 * 1024; // must match app.upload.max-size-bytes

const ACCEPTED_TYPES = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/gif',
  'image/avif',
];

/**
 * Client-side pre-flight validation.
 *
 * <p>Checking in the browser is not a security control — anyone can call the
 * endpoint with curl. It exists purely to save the user a 5 MB round trip that
 * was always going to be rejected, and to give an instant, specific message
 * instead of a generic one after a slow upload.
 *
 * @returns {string|null} an error message, or null when the file looks fine
 */
export function validateImageFile(file) {
  if (!file) return 'Choose an image first.';

  if (file.size === 0) return 'That file is empty.';

  if (file.size > MAX_BYTES) {
    const mb = (file.size / (1024 * 1024)).toFixed(1);
    return `That image is ${mb} MB. The limit is ${MAX_BYTES / (1024 * 1024)} MB.`;
  }

  if (!ACCEPTED_TYPES.includes(file.type)) {
    return `Unsupported file type "${file.type || 'unknown'}". Use JPG, PNG, WEBP, GIF or AVIF.`;
  }

  return null;
}

/** The backend's real limit, so the UI never disagrees with the server. */
export async function fetchUploadLimits() {
  try {
    return await axiosInstance.get('/products/images/limits');
  } catch {
    // The public GET may be blocked by a gateway rule; fall back to the constant.
    return { maxBytes: MAX_BYTES, maxMb: MAX_BYTES / (1024 * 1024), directUploadAvailable: false };
  }
}

/**
 * Uploads through the API and returns the stored file.
 *
 * <p>{@link FormData} is the browser's representation of
 * {@code multipart/form-data}. The request interceptor deletes the explicit
 * {@code Content-Type} header for FormData, because the browser has to set it
 * itself — it must include the generated boundary string, and a hand-written
 * {@code multipart/form-data} without a boundary is unparseable on the server.
 *
 * @param {File} file
 * @param {(percent: number) => void} [onProgress] upload progress 0-100
 * @returns {Promise<{key: string, url: string, contentType: string, sizeBytes: number}>}
 */
export async function uploadImage(file, onProgress) {
  const invalid = validateImageFile(file);
  if (invalid) {
    throw new Error(invalid);
  }

  const form = new FormData();
  form.append('file', file);

  return axiosInstance.post('/products/images', form, {
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return;
      // Math.round rather than a raw percentage, which can be 66.6666...
      onProgress(Math.round((event.loaded / event.total) * 100));
    },
  });
}

/**
 * Uploads an image AND creates/updates the product in one request.
 *
 * <p>Two multipart parts: the file and the product as JSON. The controller
 * declares them with {@code @RequestPart("file")} and
 * {@code @RequestPart("product")}, and Spring parses the second part back into a
 * DTO because it is declared as JSON. This is what makes the admin form atomic:
 * either the image and the product both exist, or neither does.
 */
export async function createProductWithImage(product, file, onProgress) {
  const invalid = validateImageFile(file);
  if (invalid) throw new Error(invalid);

  const form = new FormData();
  form.append('file', file);
  // A Blob with an explicit JSON type: FormData can only send text or files, so
  // an object has to be stringified — and Blob is what makes the server treat
  // the part as JSON rather than as a plain form field.
  form.append(
    'product',
    new Blob([JSON.stringify(product)], { type: 'application/json' })
  );

  return axiosInstance.post('/products/with-image', form, {
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return;
      onProgress(Math.round((event.loaded / event.total) * 100));
    },
  });
}

/** Uploads an image and attaches it to a product that already exists. */
export async function attachImageToProduct(productId, file, onProgress) {
  const invalid = validateImageFile(file);
  if (invalid) throw new Error(invalid);

  const form = new FormData();
  form.append('file', file);

  return axiosInstance.post(`/products/${productId}/image`, form, {
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return;
      onProgress(Math.round((event.loaded / event.total) * 100));
    },
  });
}

/**
 * Asks the backend for a presigned S3 URL and PUTs the bytes straight to S3.
 *
 * <p>{@code onUploadProgress} works here too, and this is the path where it
 * matters most: on a slow connection the user watches a real progress bar
 * instead of a spinner, because the browser is genuinely tracking the transfer.
 *
 * <p>Falls back to {@link uploadImage} when the backend is not on S3, so the
 * caller does not need to branch.
 */
export async function uploadViaPresignedUrl(file, onProgress) {
  const invalid = validateImageFile(file);
  if (invalid) throw new Error(invalid);

  let ticket;
  try {
    ticket = await axiosInstance.post(
      `/products/images/presign?contentType=${encodeURIComponent(file.type)}&folder=products`
    );
  } catch (err) {
    // 409 means "not on S3" — the expected outcome with the local backend.
    if (err.status === 409) {
      return uploadImage(file, onProgress);
    }
    throw err;
  }

  if (!ticket?.uploadUrl) {
    return uploadImage(file, onProgress);
  }

  // A RAW axios call, deliberately: it must not go through axiosInstance, or the
  // request interceptor would attach our Bearer token and a JSON Content-Type
  // to a request going to AWS. S3 rejects a request whose Content-Type does not
  // match what was signed (SignatureDoesNotMatch), and sending the auth token to
  // a third party is a credential leak waiting to happen.
  await axios.put(ticket.uploadUrl, file, {
    headers: ticket.requiredHeaders || { 'Content-Type': file.type },
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return;
      onProgress(Math.round((event.loaded / event.total) * 100));
    },
  });

  return { key: ticket.key, url: ticket.publicUrl, contentType: file.type, sizeBytes: file.size };
}

// JWT token ke payload (beech wala part) ko decode karta hai bina kisi
// library ke. Token format: header.payload.signature — humein sirf
// payload chahiye (username aur role uske andar hain).
export function decodeToken(token) {
  try {
    const payload = token.split('.')[1];
    const decoded = JSON.parse(atob(payload));
    return decoded; // { sub: username, role: "...", iat, exp }
  } catch (err) {
    return null;
  }
}

package com.aditi.product_service.domain.model;

public class Product {
    private Long id;
    private String name;
    private Double price;
    private String imageUrl;
    private String category;
    private String description;

    public Product() {}

    public Product(Long id, String name, Double price, String imageUrl,String category,String description) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.category = category;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getCategory(){ return category; }
    public void setCategory(String category){ this.category = category; }
    public String getDescription(){ return description; }
    public void setDescription(String description){ this.description = description; }
}
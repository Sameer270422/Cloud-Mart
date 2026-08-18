package com.cloudmart.product.dto;

import java.util.List;

public record CategoryNode(String category, List<String> subcategories) {}

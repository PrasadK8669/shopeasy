export interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  stock: number;
  category: string;
  imageUrl?: string | null;
}

// Matches the "Page" JSON that Spring returns
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CartItem {
  product: Product;
  quantity: number;
}

export interface CategoryCount {
  name: string;
  count: number;
}

export interface ProductQuery {
  search?: string;
  category?: string;
  minPrice?: number | null;
  maxPrice?: number | null;
  inStock?: boolean;
  sort?: string;
  page?: number;
  size?: number;
}
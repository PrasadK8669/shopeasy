import { Routes } from '@angular/router';
import { Cart } from './components/cart/cart';
import { Checkout } from './components/checkout/checkout';
import { Contact } from './components/contact/contact';
import { OrderHistory } from './components/order-history/order-history';
import { ProductDetail } from './components/product-detail/product-detail';
import { ProductList } from './components/product-list/product-list';

export const routes: Routes = [
  { path: '', component: ProductList },
  { path: 'products/:id', component: ProductDetail },
  { path: 'cart', component: Cart },
  { path: 'checkout', component: Checkout },
  { path: 'orders', component: OrderHistory },
  { path: 'contact', component: Contact },
  { path: '**', redirectTo: '' },
];
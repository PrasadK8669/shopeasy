import { Routes } from '@angular/router';
import { Cart } from './components/cart/cart';
import { Checkout } from './components/checkout/checkout';
import { ProductList } from './components/product-list/product-list';

export const routes: Routes = [
  { path: '', component: ProductList },
  { path: 'cart', component: Cart },
  { path: 'checkout', component: Checkout },
  { path: '**', redirectTo: '' },
];
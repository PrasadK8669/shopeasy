import { Routes } from '@angular/router';
import { Cart } from './components/cart/cart';
import { Checkout } from './components/checkout/checkout';
import { Contact } from './components/contact/contact';
import { Login } from './components/login/login';
import { OrderHistory } from './components/order-history/order-history';
import { ProductDetail } from './components/product-detail/product-detail';
import { ProductList } from './components/product-list/product-list';
import { Register } from './components/register/register';
import { authGuard, guestGuard } from './guards/auth.guards';

export const routes: Routes = [
  { path: '', component: ProductList },
  { path: 'products/:id', component: ProductDetail },
  { path: 'cart', component: Cart },
  { path: 'checkout', component: Checkout, canActivate: [authGuard] },
  { path: 'orders', component: OrderHistory, canActivate: [authGuard] },
  { path: 'contact', component: Contact },
  { path: 'login', component: Login, canActivate: [guestGuard] },
  { path: 'register', component: Register, canActivate: [guestGuard] },
  { path: '**', redirectTo: '' },
];
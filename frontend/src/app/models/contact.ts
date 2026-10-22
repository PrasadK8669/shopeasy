export interface ContactRequest {
  name: string;
  email: string;
  topic: string;
  message: string;
}

export interface ContactResponse {
  id: number;
}
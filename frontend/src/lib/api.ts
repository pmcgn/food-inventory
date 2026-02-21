export interface Product {
  ean: string;
  name: string;
  category: string | null;
  image_url: string | null;
}

export interface InventoryEntry {
  id: number;
  product: Product;
  quantity: number;
  expiry_date: string | null;
  low_stock_threshold: number;
}

export interface Alert {
  type: 'low_stock' | 'expiry_soon';
  ean: string;
  product_name: string;
  detail: string;
}

export interface Settings {
  expiry_warning_days: number;
}

export interface APIError {
  code: string;
  message: string;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers }
  });
  if (res.status === 204) return undefined as T;
  const data = await res.json();
  if (!res.ok) throw data as APIError;
  return data as T;
}

export const api = {
  inventory: {
    list: () =>
      request<InventoryEntry[]>('/api/inventory'),
    add: (ean: string, expiry_date?: string) =>
      request<InventoryEntry>('/api/inventory', {
        method: 'POST',
        body: JSON.stringify({ ean, expiry_date: expiry_date ?? null })
      }),
    remove: (ean: string) =>
      request<InventoryEntry | null>(`/api/inventory/${ean}`, { method: 'DELETE' })
  },
  alerts: {
    list: () => request<Alert[]>('/api/alerts')
  },
  settings: {
    get: () => request<Settings>('/api/settings'),
    update: (s: Settings) =>
      request<Settings>('/api/settings', {
        method: 'PATCH',
        body: JSON.stringify(s)
      })
  }
};

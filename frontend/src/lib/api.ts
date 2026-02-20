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
      request<InventoryEntry[]>('/inventory'),
    add: (ean: string, expiry_date?: string) =>
      request<InventoryEntry>('/inventory', {
        method: 'POST',
        body: JSON.stringify({ ean, expiry_date: expiry_date ?? null })
      }),
    remove: (ean: string) =>
      request<InventoryEntry | null>(`/inventory/${ean}`, { method: 'DELETE' })
  },
  alerts: {
    list: () => request<Alert[]>('/alerts')
  },
  settings: {
    get: () => request<Settings>('/settings'),
    update: (s: Settings) =>
      request<Settings>('/settings', {
        method: 'PATCH',
        body: JSON.stringify(s)
      })
  }
};

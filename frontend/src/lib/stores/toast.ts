import { writable } from 'svelte/store';

export type ToastType = 'success' | 'error' | 'warning';

export interface ToastMessage {
  id: number;
  type: ToastType;
  message: string;
}

const { subscribe, update } = writable<ToastMessage[]>([]);
let nextId = 0;

export const toasts = { subscribe };

export const toast = {
  show(message: string, type: ToastType = 'success', duration = 3500) {
    const id = nextId++;
    update(list => [...list, { id, type, message }]);
    setTimeout(() => update(list => list.filter(t => t.id !== id)), duration);
  }
};

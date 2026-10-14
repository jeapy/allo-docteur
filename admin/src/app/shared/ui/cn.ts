import { ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** Fusionne des classes conditionnelles et résout les conflits Tailwind (la dernière gagne). */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

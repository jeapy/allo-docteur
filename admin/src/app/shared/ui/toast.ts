import { Injectable } from '@angular/core';
import { toast } from 'ngx-sonner';

/** Notifications éphémères (remplace MatSnackBar). Le conteneur `<ngx-sonner-toaster>` est dans `App`. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  success(message: string): void {
    toast.success(message);
  }
  error(message: string): void {
    toast.error(message);
  }
  info(message: string): void {
    toast.info(message);
  }
}

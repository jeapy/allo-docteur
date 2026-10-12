import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';

export interface ReasonDialogData {
  titleKey: string;
}

@Component({
  selector: 'app-reason-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ data.titleKey | transloco }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full">
        <mat-label>{{ 'professionals.reason' | transloco }}</mat-label>
        <textarea matInput rows="4" maxlength="1000" [formControl]="reason"></textarea>
        @if (reason.invalid) { <mat-error>{{ 'validation.required' | transloco }}</mat-error> }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | transloco }}</button>
      <button mat-flat-button [disabled]="reason.invalid" (click)="ref.close(reason.value.trim())">
        {{ 'common.confirm' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: '.full { width: 100%; min-width: 320px; }',
})
export class ReasonDialogComponent {
  protected readonly data = inject<ReasonDialogData>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<ReasonDialogComponent, string>);
  protected readonly reason = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/\S/)] });
}

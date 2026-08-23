import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ImportService } from '../../services/import.service';
import { ImportSummary } from '../../models';

@Component({
  selector: 'app-csv-upload',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './csv-upload.component.html',
  styleUrl: './csv-upload.component.css'
})
export class CsvUploadComponent {
  private readonly importService = inject(ImportService);

  selectedFile = signal<File | null>(null);
  uploading = signal(false);
  result = signal<ImportSummary | null>(null);
  uploadError = signal<string | null>(null);

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFile.set(file);
    this.result.set(null);
    this.uploadError.set(null);
  }

  onUpload(): void {
    const file = this.selectedFile();
    if (!file) return;

    this.uploading.set(true);
    this.result.set(null);
    this.uploadError.set(null);

    this.importService.importCsv(file).subscribe({
      next: (summary) => {
        this.result.set(summary);
        this.uploading.set(false);
      },
      error: () => {
        this.uploadError.set("Erreur lors de l'import du fichier CSV.");
        this.uploading.set(false);
      }
    });
  }
}

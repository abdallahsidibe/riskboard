import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ImportSummary } from '../models';

@Injectable({ providedIn: 'root' })
export class ImportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/import`;

  importCsv(file: File): Observable<ImportSummary> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportSummary>(this.baseUrl, formData);
  }
}

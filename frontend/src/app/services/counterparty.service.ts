import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CounterpartyDto } from '../models';

@Injectable({ providedIn: 'root' })
export class CounterpartyService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/counterparties`;

  getAll(): Observable<CounterpartyDto[]> {
    return this.http.get<CounterpartyDto[]>(this.baseUrl);
  }
}

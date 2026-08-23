import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DerogationRequestDto, LimitType } from '../models';

export interface CreateDerogationRequest {
  counterpartyId: number;
  limitType: LimitType;
  amount: number;
  reason: string;
  requestedBy: string;
}

@Injectable({ providedIn: 'root' })
export class DerogationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/derogations`;

  getAll(): Observable<DerogationRequestDto[]> {
    return this.http.get<DerogationRequestDto[]>(this.baseUrl);
  }

  getPending(): Observable<DerogationRequestDto[]> {
    return this.http.get<DerogationRequestDto[]>(`${this.baseUrl}?status=PENDING`);
  }

  create(request: CreateDerogationRequest): Observable<DerogationRequestDto> {
    return this.http.post<DerogationRequestDto>(this.baseUrl, request);
  }

  approve(id: number): Observable<DerogationRequestDto> {
    return this.http.patch<DerogationRequestDto>(`${this.baseUrl}/${id}/approve`, {});
  }

  reject(id: number): Observable<DerogationRequestDto> {
    return this.http.patch<DerogationRequestDto>(`${this.baseUrl}/${id}/reject`, {});
  }
}

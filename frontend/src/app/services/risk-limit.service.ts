import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { RiskLimitDto, LimitCheckResult, LimitType } from '../models';

@Injectable({ providedIn: 'root' })
export class RiskLimitService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/risklimits`;

  getAll(): Observable<RiskLimitDto[]> {
    return this.http.get<RiskLimitDto[]>(this.baseUrl);
  }

  checkLimit(counterpartyId: number, limitType: LimitType, amount: number): Observable<LimitCheckResult> {
    const params = new HttpParams()
      .set('counterpartyId', counterpartyId.toString())
      .set('limitType', limitType)
      .set('amount', amount.toString());
    return this.http.get<LimitCheckResult>(`${this.baseUrl}/check`, { params });
  }
}

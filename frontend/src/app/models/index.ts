export type LimitType = 'CREDIT' | 'MARKET' | 'LIQUIDITY';
export type AlertLevel = 'GREEN' | 'ORANGE' | 'RED';
export type DerogationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface CounterpartyDto {
  id: number;
  name: string;
  ricosCode: string;
  country: string;
  sector: string;
}

export interface RiskLimitDto {
  id: number;
  counterpartyId: number;
  counterpartyName: string;
  ricosCode: string;
  country: string;
  sector: string;
  limitType: LimitType;
  maxAmount: number;
  usedAmount: number;
  currency: string;
  usageRate: number;
  alertLevel: AlertLevel;
  lastUpdated: string;
}

export interface DerogationRequestDto {
  id: number;
  counterpartyId: number;
  counterpartyName: string;
  ricosCode: string;
  requestedBy: string;
  amount: number;
  reason: string;
  status: DerogationStatus;
  limitType: LimitType;
  createdAt: string;
}

export interface LimitCheckResult {
  limitExists: boolean;
  amountWithinBounds: boolean;
  maxAmount: number | null;
  threshold150Percent: number | null;
}

export interface ImportSummary {
  successCount: number;
  errorCount: number;
  errors: { lineNumber: number; message: string }[];
}

export interface AggregatedSectorData {
  limitType: LimitType;
  sector: string;
  totalUsed: number;
}

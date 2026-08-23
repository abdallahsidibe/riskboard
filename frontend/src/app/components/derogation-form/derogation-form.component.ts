import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  Validators,
  AbstractControl,
  ValidationErrors,
  AsyncValidatorFn
} from '@angular/forms';
import { Observable, of, timer } from 'rxjs';
import { switchMap, map, catchError, debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { CounterpartyService } from '../../services/counterparty.service';
import { DerogationService, CreateDerogationRequest } from '../../services/derogation.service';
import { RiskLimitService } from '../../services/risk-limit.service';
import { CounterpartyDto, LimitType } from '../../models';

@Component({
  selector: 'app-derogation-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './derogation-form.component.html',
  styleUrl: './derogation-form.component.css'
})
export class DerogationFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly counterpartyService = inject(CounterpartyService);
  private readonly derogationService = inject(DerogationService);
  private readonly riskLimitService = inject(RiskLimitService);

  counterparties = signal<CounterpartyDto[]>([]);
  submitSuccess = signal(false);
  submitError = signal<string | null>(null);

  readonly limitTypes: LimitType[] = ['CREDIT', 'MARKET', 'LIQUIDITY'];

  form = this.fb.group({
    counterpartyId: [null as number | null, Validators.required],
    limitType: ['CREDIT' as LimitType, Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(0.000001)]],
    reason: ['', [Validators.required, Validators.minLength(20)]],
    requestedBy: ['', [Validators.required, Validators.minLength(6)]]
  }, {
    asyncValidators: [this.limitCheckValidator()]
  });

  ngOnInit(): void {
    this.counterpartyService.getAll().subscribe({
      next: (data) => this.counterparties.set(data),
      error: () => {}
    });

    // Re-run async validation when counterpartyId or limitType changes
    this.form.get('counterpartyId')?.valueChanges.subscribe(() => {
      this.form.get('amount')?.updateValueAndValidity();
    });
    this.form.get('limitType')?.valueChanges.subscribe(() => {
      this.form.get('amount')?.updateValueAndValidity();
    });
  }

  private limitCheckValidator(): AsyncValidatorFn {
    return (control: AbstractControl): Observable<ValidationErrors | null> => {
      const group = control;
      const counterpartyId = group.get('counterpartyId')?.value as number | null;
      const limitType = group.get('limitType')?.value as LimitType | null;
      const amount = group.get('amount')?.value as number | null;

      if (!counterpartyId || !limitType || !amount || amount <= 0) {
        return of(null);
      }

      return timer(500).pipe(
        switchMap(() =>
          this.riskLimitService.checkLimit(counterpartyId, limitType, amount).pipe(
            map(result => {
              if (!result.limitExists) {
                return { noLimit: true };
              }
              if (!result.amountWithinBounds) {
                return { exceedsMax: { threshold: result.threshold150Percent } };
              }
              return null;
            }),
            catchError(() => of(null))
          )
        )
      );
    };
  }

  get counterpartyIdControl() { return this.form.get('counterpartyId')!; }
  get limitTypeControl() { return this.form.get('limitType')!; }
  get amountControl() { return this.form.get('amount')!; }
  get reasonControl() { return this.form.get('reason')!; }
  get requestedByControl() { return this.form.get('requestedBy')!; }

  hasNoLimit(): boolean {
    return this.form.errors?.['noLimit'] === true;
  }

  hasExceedsMax(): boolean {
    return !!this.form.errors?.['exceedsMax'];
  }

  getThreshold(): number | null {
    return this.form.errors?.['exceedsMax']?.['threshold'] ?? null;
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    const value = this.form.value;
    const request: CreateDerogationRequest = {
      counterpartyId: value.counterpartyId!,
      limitType: value.limitType!,
      amount: value.amount!,
      reason: value.reason!,
      requestedBy: value.requestedBy!
    };

    this.submitSuccess.set(false);
    this.submitError.set(null);

    this.derogationService.create(request).subscribe({
      next: () => {
        this.submitSuccess.set(true);
        this.form.reset({ limitType: 'CREDIT' });
      },
      error: () => {
        this.submitError.set('Erreur lors de la soumission de la demande.');
      }
    });
  }
}

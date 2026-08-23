import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DerogationService } from '../../services/derogation.service';
import { DerogationRequestDto } from '../../models';

@Component({
  selector: 'app-derogation-validation',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './derogation-validation.component.html',
  styleUrl: './derogation-validation.component.css'
})
export class DerogationValidationComponent implements OnInit {
  private readonly derogationService = inject(DerogationService);

  pendingRequests = signal<DerogationRequestDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  actionError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadPending();
  }

  private loadPending(): void {
    this.loading.set(true);
    this.derogationService.getPending().subscribe({
      next: (data) => {
        this.pendingRequests.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Erreur lors du chargement des demandes en attente.');
        this.loading.set(false);
      }
    });
  }

  approve(id: number): void {
    this.actionError.set(null);
    this.derogationService.approve(id).subscribe({
      next: () => this.loadPending(),
      error: () => this.actionError.set('Erreur lors de la validation.')
    });
  }

  reject(id: number): void {
    this.actionError.set(null);
    this.derogationService.reject(id).subscribe({
      next: () => this.loadPending(),
      error: () => this.actionError.set('Erreur lors du rejet.')
    });
  }
}

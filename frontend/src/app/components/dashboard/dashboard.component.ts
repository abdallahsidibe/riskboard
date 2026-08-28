import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RiskLimitService } from '../../services/risk-limit.service';
import { RiskLimitDto, AlertLevel, LimitType, AggregatedSectorData } from '../../models';

type SortColumn =
  | 'counterpartyName'
  | 'limitType'
  | 'sector'
  | 'maxAmount'
  | 'usedAmount'
  | 'usageRate'
  | 'alertLevel';

type SortDirection = 'asc' | 'desc';

interface SortConfig {
  column: SortColumn;
  direction: SortDirection;
}

const DEFAULT_SORT: SortConfig[] = [
  { column: 'counterpartyName', direction: 'asc' },
  { column: 'limitType', direction: 'asc' },
  { column: 'sector', direction: 'asc' },
  { column: 'maxAmount', direction: 'desc' },
  { column: 'usedAmount', direction: 'desc' },
  { column: 'usageRate', direction: 'desc' },
  { column: 'alertLevel', direction: 'asc' },
];

const PAGE_SIZE = 10;

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  private readonly riskLimitService = inject(RiskLimitService);

  limits = signal<RiskLimitDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  filterText = signal('');
  selectedLimitType = signal<LimitType | null>(null);
  currentPage = signal(1);

  sortConfigs = signal<SortConfig[]>([...DEFAULT_SORT]);
  activeSort = signal<SortConfig | null>(null);

  ngOnInit(): void {
    this.riskLimitService.getAll().subscribe({
      next: (data) => {
        this.limits.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Erreur lors du chargement des limites de risque.');
        this.loading.set(false);
      }
    });
  }

  filteredLimits = computed(() => {
    const filter = this.filterText().toLowerCase();
    return this.limits().filter(l =>
      l.counterpartyName.toLowerCase().includes(filter)
    );
  });

  sortedLimits = computed(() => {
    const items = [...this.filteredLimits()];
    const active = this.activeSort();

    if (active) {
      const configs = [active, ...DEFAULT_SORT.filter(d => d.column !== active.column)];
      return this.applySort(items, configs);
    }

    return this.applySort(items, DEFAULT_SORT);
  });

  private applySort(items: RiskLimitDto[], configs: SortConfig[]): RiskLimitDto[] {
    return items.sort((a, b) => {
      for (const config of configs) {
        const cmp = this.compareBy(a, b, config.column);
        if (cmp !== 0) return config.direction === 'asc' ? cmp : -cmp;
      }
      return 0;
    });
  }

  private compareBy(a: RiskLimitDto, b: RiskLimitDto, col: SortColumn): number {
    const va = a[col];
    const vb = b[col];
    if (typeof va === 'number' && typeof vb === 'number') return va - vb;
    return String(va).localeCompare(String(vb));
  }

  paginatedLimits = computed(() => {
    const page = this.currentPage();
    const start = (page - 1) * PAGE_SIZE;
    return this.sortedLimits().slice(start, start + PAGE_SIZE);
  });

  totalPages = computed(() => Math.max(1, Math.ceil(this.sortedLimits().length / PAGE_SIZE)));

  aggregatedData = computed((): AggregatedSectorData[] => {
    const type = this.selectedLimitType();
    if (!type) return [];
    const map = new Map<string, number>();
    for (const l of this.limits()) {
      if (l.limitType === type) {
        map.set(l.sector, (map.get(l.sector) ?? 0) + l.usedAmount);
      }
    }
    return Array.from(map.entries()).map(([sector, totalUsed]) => ({
      limitType: type,
      sector,
      totalUsed
    }));
  });

  setLimitTypeFilter(type: LimitType | null): void {
    this.selectedLimitType.set(type);
    this.currentPage.set(1);
  }

  setFilter(value: string): void {
    this.filterText.set(value);
    this.currentPage.set(1);
  }

  sortBy(col: SortColumn): void {
    const current = this.activeSort();
    if (current?.column === col) {
      this.activeSort.set({ column: col, direction: current.direction === 'asc' ? 'desc' : 'asc' });
    } else {
      const defaultDir = DEFAULT_SORT.find(d => d.column === col)?.direction ?? 'asc';
      this.activeSort.set({ column: col, direction: defaultDir });
    }
    this.currentPage.set(1);
  }

  getSortIcon(col: SortColumn): string {
    const active = this.activeSort();
    if (active?.column === col) {
      return active.direction === 'asc' ? '↑' : '↓';
    }
    return '↕';
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages()) {
      this.currentPage.set(page);
    }
  }

  pages = computed(() => {
    const total = this.totalPages();
    return Array.from({ length: total }, (_, i) => i + 1);
  });

  alertClass(level: AlertLevel): string {
    switch (level) {
      case 'GREEN': return 'badge badge-green';
      case 'ORANGE': return 'badge badge-orange';
      case 'RED': return 'badge badge-red';
    }
  }

  alertIcon(level: AlertLevel): string {
    return level === 'RED' ? ' ⚠' : '';
  }
}

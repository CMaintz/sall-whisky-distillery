import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { SallWhiskyService } from '../../../core/services/sall-whisky.service';
import { WhiskyProdukt } from '../../../core/models/models';
import { WhiskyListComponent } from './whisky-list.component';

function produkt(overrides: Partial<WhiskyProdukt> = {}): WhiskyProdukt {
  return {
    id: 'w1', navn: 'MULD', alkoholProcent: 65, antalLiter: 40, literVandTilfojet: 0,
    whiskyType: 'Cask Strength', antalFlasker: 0, fadTapninger: [], flasker: [],
    ...overrides
  };
}

describe('WhiskyListComponent', () => {
  let fixture: ComponentFixture<WhiskyListComponent>;
  let component: WhiskyListComponent;
  let service: jasmine.SpyObj<SallWhiskyService>;

  beforeEach(async () => {
    (window as any).bootstrap = { Modal: class { show() {} hide() {} } };
    service = jasmine.createSpyObj<SallWhiskyService>('SallWhiskyService',
      ['getWhiskyProdukter', 'opretFlasker', 'getFadeKlar']);
    service.getWhiskyProdukter.and.returnValue(of([produkt(), produkt({ id: 'w2', navn: 'TØRV' })]));
    service.getFadeKlar.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [WhiskyListComponent],
      providers: [{ provide: SallWhiskyService, useValue: service }]
    }).compileComponents();

    fixture = TestBed.createComponent(WhiskyListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads and renders the products', () => {
    expect(component.loading()).toBeFalse();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('MULD');
    expect(text).toContain('TØRV');
  });

  it('toggles the selected product', () => {
    const wp = component.produkter()[0];
    component.vaelgProdukt(wp);
    expect(component.valgtProdukt()).toBe(wp);
    component.vaelgProdukt(wp);
    expect(component.valgtProdukt()).toBeNull();
  });

  it('replaces the product with the bottled result', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    const bottled = produkt({ antalLiter: 0, antalFlasker: 40 });
    service.opretFlasker.and.returnValue(of(bottled));

    component.opretFlasker(component.produkter()[0]);

    expect(service.opretFlasker).toHaveBeenCalledWith('w1');
    expect(component.produkter()[0].antalFlasker).toBe(40);
    expect(component.flaskningBusy()).toBeNull();
  });

  it('does nothing when bottling is not confirmed', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    component.opretFlasker(component.produkter()[0]);
    expect(service.opretFlasker).not.toHaveBeenCalled();
  });
});

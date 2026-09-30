import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SallWhiskyService } from './sall-whisky.service';

describe('SallWhiskyService', () => {
  let service: SallWhiskyService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(SallWhiskyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('fetches barrels ready for bottling', () => {
    service.getFadeKlar().subscribe(fade => expect(fade.length).toBe(1));

    const req = http.expectOne('http://localhost:8080/api/fade/klar');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 'f1' }]);
  });

  it('posts a fill request with the distillation items', () => {
    const body = { paafyldninger: [{ destilleringId: 'd1', liter: 20, medarbejder: 'Chris' }] };
    service.paafyldFad('f1', body).subscribe();

    const req = http.expectOne('http://localhost:8080/api/fade/f1/destillat');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('moves a barrel with PUT and the target shelf id', () => {
    service.flytFad('f1', 'h7').subscribe();

    const req = http.expectOne('http://localhost:8080/api/fade/f1/flyt');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ hyldeId: 'h7' });
    req.flush({});
  });

  it('re-barrels via the omhaeld endpoint', () => {
    service.omhaeldDestillat('fra', 'til').subscribe();

    const req = http.expectOne('http://localhost:8080/api/fade/fra/omhaeld/til');
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('sends litres of water in the request body', () => {
    service.tilfoejVand('w1', 2.5).subscribe();

    const req = http.expectOne('http://localhost:8080/api/whisky/w1/vand');
    expect(req.request.body).toEqual({ liter: 2.5 });
    req.flush({});
  });
});

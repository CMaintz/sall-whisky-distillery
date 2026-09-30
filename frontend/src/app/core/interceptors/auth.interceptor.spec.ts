import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  it('adds a Basic Authorization header to outgoing requests', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()]
    });
    const client = TestBed.inject(HttpClient);
    const http = TestBed.inject(HttpTestingController);

    client.get('/api/fade').subscribe();

    const req = http.expectOne('/api/fade');
    expect(req.request.headers.get('Authorization')).toBe(`Basic ${btoa('admin:admin')}`);
    req.flush([]);
    http.verify();
  });
});

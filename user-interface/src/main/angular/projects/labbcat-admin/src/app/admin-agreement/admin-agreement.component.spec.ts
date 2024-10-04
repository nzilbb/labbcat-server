import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAgreementComponent } from './admin-agreement.component';

describe('AdminAgreementComponent', () => {
  let component: AdminAgreementComponent;
  let fixture: ComponentFixture<AdminAgreementComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminAgreementComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminAgreementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

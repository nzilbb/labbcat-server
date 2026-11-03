import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminFormattersComponent } from './admin-formatters.component';

describe('AdminFormattersComponent', () => {
  let component: AdminFormattersComponent;
  let fixture: ComponentFixture<AdminFormattersComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminFormattersComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminFormattersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminTaskAttributeValidationComponent } from './admin-task-attribute-validation.component';

describe('AdminTaskAttributeValidationComponent', () => {
  let component: AdminTaskAttributeValidationComponent;
  let fixture: ComponentFixture<AdminTaskAttributeValidationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminTaskAttributeValidationComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminTaskAttributeValidationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

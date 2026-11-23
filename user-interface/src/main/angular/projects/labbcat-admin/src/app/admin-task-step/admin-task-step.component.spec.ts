import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminTaskStepComponent } from './admin-task-step.component';

describe('AdminTaskStepComponent', () => {
  let component: AdminTaskStepComponent;
  let fixture: ComponentFixture<AdminTaskStepComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminTaskStepComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminTaskStepComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

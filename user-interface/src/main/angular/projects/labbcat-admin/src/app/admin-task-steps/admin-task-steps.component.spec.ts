import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminTaskStepsComponent } from './admin-task-steps.component';

describe('AdminTaskStepsComponent', () => {
  let component: AdminTaskStepsComponent;
  let fixture: ComponentFixture<AdminTaskStepsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminTaskStepsComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminTaskStepsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

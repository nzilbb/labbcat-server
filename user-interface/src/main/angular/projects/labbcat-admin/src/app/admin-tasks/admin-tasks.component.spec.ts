import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminTasksComponent } from './admin-tasks.component';

describe('AdminTasksComponent', () => {
  let component: AdminTasksComponent;
  let fixture: ComponentFixture<AdminTasksComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminTasksComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminTasksComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

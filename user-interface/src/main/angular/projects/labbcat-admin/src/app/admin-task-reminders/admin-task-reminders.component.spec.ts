import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminTaskRemindersComponent } from './admin-task-reminders.component';

describe('AdminTaskRemindersComponent', () => {
  let component: AdminTaskRemindersComponent;
  let fixture: ComponentFixture<AdminTaskRemindersComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminTaskRemindersComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminTaskRemindersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

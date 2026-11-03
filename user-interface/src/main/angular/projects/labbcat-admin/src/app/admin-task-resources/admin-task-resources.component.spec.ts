import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminTaskResourcesComponent } from './admin-task-resources.component';

describe('AdminTaskResourcesComponent', () => {
  let component: AdminTaskResourcesComponent;
  let fixture: ComponentFixture<AdminTaskResourcesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminTaskResourcesComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminTaskResourcesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

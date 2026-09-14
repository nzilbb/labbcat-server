import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminTaskAttributesComponent } from './admin-task-attributes.component';

describe('AdminTaskAttributesComponent', () => {
  let component: AdminTaskAttributesComponent;
  let fixture: ComponentFixture<AdminTaskAttributesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminTaskAttributesComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminTaskAttributesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

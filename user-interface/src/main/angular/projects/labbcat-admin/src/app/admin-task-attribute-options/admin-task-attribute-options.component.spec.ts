import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminTaskAttributeOptionsComponent } from './admin-task-attribute-options.component';

describe('AdminTaskAttributeOptionsComponent', () => {
  let component: AdminTaskAttributeOptionsComponent;
  let fixture: ComponentFixture<AdminTaskAttributeOptionsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminTaskAttributeOptionsComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminTaskAttributeOptionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

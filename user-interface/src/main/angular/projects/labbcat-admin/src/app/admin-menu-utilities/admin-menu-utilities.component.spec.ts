import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminMenuUtilitiesComponent } from './admin-menu-utilities.component';

describe('AdminMenuUtilitiesComponent', () => {
  let component: AdminMenuUtilitiesComponent;
  let fixture: ComponentFixture<AdminMenuUtilitiesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminMenuUtilitiesComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminMenuUtilitiesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

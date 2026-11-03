import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminUpgradeComponent } from './admin-upgrade.component';

describe('AdminUpgradeComponent', () => {
  let component: AdminUpgradeComponent;
  let fixture: ComponentFixture<AdminUpgradeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminUpgradeComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminUpgradeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

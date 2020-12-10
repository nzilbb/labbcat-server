import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { IpaHelperComponent } from './ipa-helper.component';

describe('IpaHelperComponent', () => {
  let component: IpaHelperComponent;
  let fixture: ComponentFixture<IpaHelperComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ IpaHelperComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IpaHelperComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

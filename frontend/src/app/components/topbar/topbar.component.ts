import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [RouterLink, IconComponent],
  templateUrl: './topbar.component.html',
  styleUrl: './topbar.component.css'
})
export class TopbarComponent {}

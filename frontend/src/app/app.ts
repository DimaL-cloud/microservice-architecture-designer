import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { TopbarComponent } from './components/topbar/topbar.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, TopbarComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {}

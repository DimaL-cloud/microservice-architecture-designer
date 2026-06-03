import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Icon } from './shared/ui/icon';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [RouterLink, Icon],
  templateUrl: './topbar.html'
})
export class Topbar {}

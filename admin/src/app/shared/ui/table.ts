import { Directive } from '@angular/core';

/** Tableau natif stylé (voir styles/_table.scss) : `<table uiTable>` dans un conteneur `.ui-table-wrap`. */
@Directive({ selector: 'table[uiTable]', host: { class: 'ui-table' } })
export class UiTable {}

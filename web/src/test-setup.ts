import '@testing-library/jest-dom/vitest';

// jsdom has no modal <dialog>; the kit's Dialog and BottomSheet only need open and close.
HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
  this.open = true;
};
HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
  this.open = false;
};

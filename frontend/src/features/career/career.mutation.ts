/** Synchronous ownership prevents two handlers entering before React renders busy state. */
export class CareerMutationGate {
  private owner: symbol | null = null;
  private readonly changed: (busy: boolean) => void;
  constructor(changed: (busy: boolean) => void) { this.changed = changed; }
  get busy(): boolean { return this.owner !== null; }
  acquire(): (() => void) | null {
    if (this.owner !== null) return null;
    const token = Symbol('career mutation'); this.owner = token; this.changed(true);
    return () => { if (this.owner === token) { this.owner = null; this.changed(false); } };
  }
}

// Physical screen coordinates keep left/right intuitive in both RTL and LTR rails.
export function nearest(rect,candidates,key){
  const vertical=key==='ArrowUp'||key==='ArrowDown',positive=key==='ArrowDown'||key==='ArrowRight';
  const center=r=>vertical?[(r.top+r.bottom)/2,(r.left+r.right)/2]:[(r.left+r.right)/2,(r.top+r.bottom)/2];
  const [primary,secondary]=center(rect);let best=null,score=Infinity;
  for(const candidate of candidates){const r=candidate.rect,[p,s]=center(r),distance=(p-primary)*(positive?1:-1);if(distance<2)continue;
    const overlap=vertical?Math.min(rect.right,r.right)-Math.max(rect.left,r.left):Math.min(rect.bottom,r.bottom)-Math.max(rect.top,r.top);
    const value=distance+Math.abs(s-secondary)*2+(overlap<0?1000:0);if(value<score){score=value;best=candidate.value;}
  }return best;
}

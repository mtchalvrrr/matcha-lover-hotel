(() => {
 const grid=document.getElementById("roomGrid"); if(!grid)return;
 const controls={type:document.getElementById("roomTypeFilter"),min:document.getElementById("roomPriceMin"),max:document.getElementById("roomPriceMax"),capacity:document.getElementById("roomCapacityFilter"),sort:document.getElementById("roomSort")};
 const cards=Array.from(grid.querySelectorAll(".room-card")), empty=document.getElementById("roomFilterEmpty");
 const prices=cards.map(c=>Number(c.dataset.price)).filter(Number.isFinite);
 const floor=prices.length?Math.floor(Math.min(...prices)/100)*100:0;
 const ceiling=prices.length?Math.ceil(Math.max(...prices)/100)*100:10000, cap=Math.max(ceiling,floor+100);
 [controls.min,controls.max].forEach(x=>{x.min=floor;x.max=cap;});
 controls.min.value=floor;controls.max.value=cap;
 const peso=v=>"₱"+Number(v).toLocaleString("en-PH");
 function apply(){
  let low=Number(controls.min.value),high=Number(controls.max.value);
  if(low>high){if(document.activeElement===controls.min)controls.max.value=low;else controls.min.value=high;low=Number(controls.min.value);high=Number(controls.max.value);}
  document.getElementById("roomPriceLabel").textContent=peso(low)+" – "+peso(high);
  document.getElementById("roomPriceMinValue").textContent=peso(low);
  document.getElementById("roomPriceMaxValue").textContent=peso(high);
  const type=controls.type.value.toLowerCase(),capacity=Number(controls.capacity.value||1);
  const visible=cards.filter(c=>{const price=Number(c.dataset.price);return(!type||(c.dataset.roomType||"").toLowerCase()===type)&&price>=low&&price<=high&&Number(c.dataset.capacity)>=capacity;});
  visible.sort((a,b)=>{if(controls.sort.value==="price-asc")return Number(a.dataset.price)-Number(b.dataset.price);if(controls.sort.value==="price-desc")return Number(b.dataset.price)-Number(a.dataset.price);if(controls.sort.value==="capacity-desc")return Number(b.dataset.capacity)-Number(a.dataset.capacity);if(controls.sort.value==="name")return(a.dataset.name||"").localeCompare(b.dataset.name||"");return cards.indexOf(a)-cards.indexOf(b);});
  visible.forEach(c=>{c.hidden=false;grid.appendChild(c);});cards.filter(c=>!visible.includes(c)).forEach(c=>c.hidden=true);if(empty)empty.hidden=visible.length>0;
 }
 Object.values(controls).forEach(x=>{x.addEventListener("input",apply);x.addEventListener("change",apply);});
 document.getElementById("clearRoomFilters")?.addEventListener("click",()=>{controls.type.value="";controls.min.value=floor;controls.max.value=cap;controls.capacity.value="1";controls.sort.value="default";apply();});
 grid.querySelectorAll(".room-card-photo-strip").forEach(strip=>{let drag=false,start=0,scroll=0;strip.addEventListener("pointerdown",e=>{drag=true;start=e.clientX;scroll=strip.scrollLeft;strip.setPointerCapture(e.pointerId);});strip.addEventListener("pointermove",e=>{if(drag)strip.scrollLeft=scroll-(e.clientX-start);});const stop=()=>drag=false;strip.addEventListener("pointerup",stop);strip.addEventListener("pointercancel",stop);});
 apply();
})();
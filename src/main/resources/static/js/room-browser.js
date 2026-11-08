(() => {
  const grid = document.getElementById("roomGrid");
  if (!grid) return;

  const controls = {
    type: document.getElementById("roomTypeFilter"),
    price: document.getElementById("roomPriceFilter"),
    capacity: document.getElementById("roomCapacityFilter"),
    amenity: document.getElementById("roomAmenityFilter"),
    sort: document.getElementById("roomSort")
  };
  const emptyState = document.getElementById("roomFilterEmpty");
  const cards = Array.from(grid.querySelectorAll(".room-card"));

  function applyFilters() {
    const type = controls.type.value.trim().toLowerCase();
    const maxPrice = controls.price.value === "" ? null : Number(controls.price.value);
    const minCapacity = Math.max(1, Number(controls.capacity.value || 1));
    const amenity = controls.amenity.value.trim().toLowerCase();

    const visible = cards.filter(card => {
      const matchesType = !type || (card.dataset.roomType || "").toLowerCase() === type;
      const price = Number(card.dataset.price);
      const capacity = Number(card.dataset.capacity);
      const amenities = (card.dataset.amenities || "").toLowerCase();
      return matchesType
        && (maxPrice === null || (Number.isFinite(price) && price <= maxPrice))
        && Number.isFinite(capacity) && capacity >= minCapacity
        && (!amenity || amenities.includes(amenity));
    });

    const sort = controls.sort.value;
    visible.sort((a, b) => {
      const priceA = Number(a.dataset.price);
      const priceB = Number(b.dataset.price);
      const capacityA = Number(a.dataset.capacity);
      const capacityB = Number(b.dataset.capacity);
      if (sort === "price-asc") return priceA - priceB;
      if (sort === "price-desc") return priceB - priceA;
      if (sort === "capacity-desc") return capacityB - capacityA;
      if (sort === "name") return (a.dataset.name || "").localeCompare(b.dataset.name || "");
      return cards.indexOf(a) - cards.indexOf(b);
    });

    visible.forEach(card => {
      card.hidden = false;
      grid.appendChild(card);
    });
    cards.filter(card => !visible.includes(card)).forEach(card => { card.hidden = true; });
    if (emptyState) emptyState.hidden = visible.length > 0;
  }

  Object.values(controls).forEach(control => {
    control.addEventListener("input", applyFilters);
    control.addEventListener("change", applyFilters);
  });

  document.getElementById("clearRoomFilters")?.addEventListener("click", () => {
    controls.type.value = "";
    controls.price.value = "";
    controls.capacity.value = "1";
    controls.amenity.value = "";
    controls.sort.value = "default";
    applyFilters();
  });

  applyFilters();
})();

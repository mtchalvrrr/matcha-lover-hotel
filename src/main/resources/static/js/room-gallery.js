(() => {
  const gallery = document.getElementById("roomGallery");
  if (!gallery) return;
  const swipeSurface = document.getElementById("galleryOpenLightbox");
  const activeImage = document.getElementById("galleryActiveImage");
  const thumbnails = Array.from(gallery.querySelectorAll(".gallery-thumbnail"));
  const lightbox = document.getElementById("roomLightbox");
  const lightboxImage = document.getElementById("galleryLightboxImage");
  if (!activeImage || thumbnails.length === 0) return;

  let activeIndex = 0, startX = 0, startY = 0, dragging = false, moved = false, suppressClick = false;
  function showPhoto(index) {
    activeIndex = (index + thumbnails.length) % thumbnails.length;
    const source = thumbnails[activeIndex].dataset.photoSrc;
    activeImage.src = source;
    lightboxImage.src = source;
    thumbnails.forEach((thumbnail, position) => {
      thumbnail.classList.toggle("is-active", position === activeIndex);
      thumbnail.setAttribute("aria-current", position === activeIndex ? "true" : "false");
    });
  }
  document.getElementById("galleryPrevious")?.addEventListener("click", () => showPhoto(activeIndex - 1));
  document.getElementById("galleryNext")?.addEventListener("click", () => showPhoto(activeIndex + 1));
  thumbnails.forEach((thumbnail, index) => thumbnail.addEventListener("click", () => showPhoto(index)));

  swipeSurface?.addEventListener("pointerdown", event => {
    if (event.pointerType === "mouse" && event.button !== 0) return;
    dragging = true; moved = false; startX = event.clientX; startY = event.clientY;
    swipeSurface.setPointerCapture?.(event.pointerId);
  });
  swipeSurface?.addEventListener("pointermove", event => {
    if (!dragging) return;
    const dx = event.clientX - startX, dy = event.clientY - startY;
    if (Math.abs(dx) > 12 && Math.abs(dx) > Math.abs(dy)) moved = true;
  });
  const finishSwipe = event => {
    if (!dragging) return;
    const dx = event.clientX - startX, dy = event.clientY - startY;
    if (Math.abs(dx) > 45 && Math.abs(dx) > Math.abs(dy) * 1.15) {
      showPhoto(activeIndex + (dx < 0 ? 1 : -1));
      suppressClick = true;
      window.setTimeout(() => { suppressClick = false; }, 350);
    }
    dragging = false;
  };
  swipeSurface?.addEventListener("pointerup", finishSwipe);
  swipeSurface?.addEventListener("pointercancel", () => { dragging = false; });
  swipeSurface?.addEventListener("click", event => {
    if (suppressClick || moved) {
      event.preventDefault(); event.stopPropagation(); moved = false; return;
    }
    lightboxImage.src = activeImage.src;
    if (typeof lightbox.showModal === "function") lightbox.showModal();
  });
  document.getElementById("galleryCloseLightbox")?.addEventListener("click", () => lightbox.close());
  lightbox?.addEventListener("click", event => { if (event.target === lightbox) lightbox.close(); });
  lightbox?.addEventListener("keydown", event => {
    if (event.key === "ArrowLeft") showPhoto(activeIndex - 1);
    if (event.key === "ArrowRight") showPhoto(activeIndex + 1);
  });
  showPhoto(0);
})();
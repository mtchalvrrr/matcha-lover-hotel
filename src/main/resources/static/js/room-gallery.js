(() => {
  // Gallery setup runs separately so a gallery issue cannot disable the popup.
  const gallery = document.getElementById("roomGallery");
  if (!gallery) return;
  const activeImage = document.getElementById("galleryActiveImage");
  const thumbnailStrip = document.getElementById("galleryThumbnails");
  const lightbox = document.getElementById("roomLightbox");
  const lightboxImage = document.getElementById("galleryLightboxImage");
  const status = document.getElementById("galleryCarouselStatus");
  if (!activeImage || !thumbnailStrip) return;

  // Used only when the room has no uploaded photos yet. Replace these with
  // the hotel's own room photos before the site goes live.
  const samplePhotos = [
    { src: "https://images.unsplash.com/photo-1725962479542-1be0a6b0d444?auto=format&fit=crop&w=1800&q=85", alt: "Sample hotel room with a large bed and television" },
    { src: "https://images.unsplash.com/photo-1652161854125-1f5289c13eac?auto=format&fit=crop&w=1800&q=85", alt: "Sample bright hotel bedroom" },
    { src: "https://images.unsplash.com/photo-1776500587913-6e55907a738e?auto=format&fit=crop&w=1800&q=85", alt: "Sample modern hotel room with two beds and windows" },
    { src: "https://images.unsplash.com/photo-1784295070532-13b4ec3fcda0?auto=format&fit=crop&w=1800&q=85", alt: "Sample hotel room with bedside table and curtains" },
    { src: "https://images.unsplash.com/photo-1668260592478-a6513b0a690e?auto=format&fit=crop&w=1800&q=85", alt: "Sample hotel room with a bed and television" }
  ];

  let thumbnails = Array.from(thumbnailStrip.querySelectorAll(".gallery-thumbnail"));
  const usingSamples = thumbnails.length < 5;
  if (usingSamples) {
    samplePhotos.slice(0, 5 - thumbnails.length).forEach((photo, index) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "gallery-thumbnail";
      button.dataset.photoSrc = photo.src;
      button.setAttribute("aria-label", `Show sample photo ${index + 1}`);
      const image = document.createElement("img");
      image.src = photo.src;
      image.alt = photo.alt;
      image.loading = index === 0 ? "eager" : "lazy";
      button.appendChild(image);
      thumbnailStrip.appendChild(button);
    });
    thumbnails = Array.from(thumbnailStrip.querySelectorAll(".gallery-thumbnail"));
    gallery.classList.add("has-sample-photos");
  }

  if (thumbnails.length === 0) return;
  const photos = thumbnails.map((thumbnail, index) => ({
    src: thumbnail.dataset.photoSrc,
    alt: thumbnail.querySelector("img")?.alt || `Room photo ${index + 1}`
  }));

  let activeIndex = 0;
  let startX = 0;
  let startY = 0;
  let dragging = false;
  let moved = false;
  let suppressClick = false;
  let autoplayTimer;

  function showPhoto(index, restartAutoplay = true) {
    activeIndex = (index + photos.length) % photos.length;
    const photo = photos[activeIndex];
    activeImage.src = photo.src;
    activeImage.alt = photo.alt;
    lightboxImage.src = photo.src;
    lightboxImage.alt = photo.alt;
    thumbnails.forEach((thumbnail, position) => {
      const active = position === activeIndex;
      thumbnail.classList.toggle("is-active", active);
      thumbnail.setAttribute("aria-current", active ? "true" : "false");
    });
    if (status) {
      status.textContent = `${activeIndex + 1} / ${photos.length}${usingSamples ? " · Includes sample photos" : ""}`;
    }
    if (restartAutoplay) startAutoplay();
  }

  function nextPhoto() { showPhoto(activeIndex + 1); }
  function previousPhoto() { showPhoto(activeIndex - 1); }
  function stopAutoplay() {
    if (autoplayTimer) window.clearInterval(autoplayTimer);
    autoplayTimer = null;
  }
  function startAutoplay() {
    stopAutoplay();
    if (photos.length < 2 || document.hidden || lightbox?.open) return;
    autoplayTimer = window.setInterval(nextPhoto, 5000);
  }

  thumbnails.forEach((thumbnail, index) => {
    thumbnail.addEventListener("click", () => showPhoto(index));
  });

  gallery.addEventListener("mouseenter", stopAutoplay);
  gallery.addEventListener("mouseleave", startAutoplay);
  gallery.addEventListener("focusin", stopAutoplay);
  gallery.addEventListener("focusout", event => {
    if (!gallery.contains(event.relatedTarget)) startAutoplay();
  });
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) stopAutoplay();
    else startAutoplay();
  });

  activeImage.parentElement?.addEventListener("pointerdown", event => {
    if (event.pointerType === "mouse" && event.button !== 0) return;
    dragging = true;
    moved = false;
    startX = event.clientX;
    startY = event.clientY;
  });
  activeImage.parentElement?.addEventListener("pointermove", event => {
    if (!dragging) return;
    const dx = event.clientX - startX;
    const dy = event.clientY - startY;
    if (Math.abs(dx) > 10 && Math.abs(dx) > Math.abs(dy)) moved = true;
  });
  const finishSwipe = event => {
    if (!dragging) return;
    const dx = event.clientX - startX;
    const dy = event.clientY - startY;
    if (Math.abs(dx) > 42 && Math.abs(dx) > Math.abs(dy) * 1.15) {
      showPhoto(activeIndex + (dx < 0 ? 1 : -1));
      suppressClick = true;
      window.setTimeout(() => { suppressClick = false; }, 350);
    }
    dragging = false;
  };
  activeImage.parentElement?.addEventListener("pointerup", finishSwipe);
  activeImage.parentElement?.addEventListener("pointercancel", () => { dragging = false; });
  activeImage.parentElement?.addEventListener("click", event => {
    if (suppressClick || moved) {
      event.preventDefault();
      event.stopPropagation();
      moved = false;
      return;
    }
    lightboxImage.src = photos[activeIndex].src;
    lightboxImage.alt = photos[activeIndex].alt;
    stopAutoplay();
    if (typeof lightbox?.showModal === "function") lightbox.showModal();
  });

  document.getElementById("galleryCloseLightbox")?.addEventListener("click", () => {
    lightbox.close();
    startAutoplay();
  });
  lightbox?.addEventListener("click", event => {
    if (event.target === lightbox) {
      lightbox.close();
      startAutoplay();
    }
  });
  lightbox?.addEventListener("keydown", event => {
    if (event.key === "ArrowLeft") previousPhoto();
    if (event.key === "ArrowRight") nextPhoto();
    if (event.key === "Escape") startAutoplay();
  });

  showPhoto(0, false);
  startAutoplay();
})();
(() => {
  const gallery = document.getElementById("roomGallery");
  if (!gallery) return;

  const activeImage = document.getElementById("galleryActiveImage");
  const thumbnails = Array.from(gallery.querySelectorAll(".gallery-thumbnail"));
  const lightbox = document.getElementById("roomLightbox");
  const lightboxImage = document.getElementById("galleryLightboxImage");
  if (!activeImage || thumbnails.length === 0) return;

  let activeIndex = 0;

  function showPhoto(index) {
    activeIndex = (index + thumbnails.length) % thumbnails.length;
    const selected = thumbnails[activeIndex];
    const source = selected.dataset.photoSrc;
    activeImage.src = source;
    lightboxImage.src = source;
    thumbnails.forEach((thumbnail, position) => {
      thumbnail.classList.toggle("is-active", position === activeIndex);
      thumbnail.setAttribute("aria-current", position === activeIndex ? "true" : "false");
    });
  }

  document.getElementById("galleryPrevious")?.addEventListener("click", () => showPhoto(activeIndex - 1));
  document.getElementById("galleryNext")?.addEventListener("click", () => showPhoto(activeIndex + 1));
  thumbnails.forEach((thumbnail, index) => {
    thumbnail.addEventListener("click", () => showPhoto(index));
  });

  document.getElementById("galleryOpenLightbox")?.addEventListener("click", () => {
    lightboxImage.src = activeImage.src;
    if (typeof lightbox.showModal === "function") lightbox.showModal();
  });
  document.getElementById("galleryCloseLightbox")?.addEventListener("click", () => lightbox.close());
  lightbox?.addEventListener("click", event => {
    if (event.target === lightbox) lightbox.close();
  });
  lightbox?.addEventListener("keydown", event => {
    if (event.key === "ArrowLeft") showPhoto(activeIndex - 1);
    if (event.key === "ArrowRight") showPhoto(activeIndex + 1);
  });

  showPhoto(0);
})();

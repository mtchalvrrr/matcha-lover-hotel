(function () {
'use strict';
const form=document.getElementById('customerRegistrationForm');if(!form)return;
const panels=[...form.querySelectorAll('.registration-panel')],indicators=[...document.querySelectorAll('[data-step-indicator]')];
const el=id=>document.getElementById(id);let step=0,applicationStarted=false,otpVerified=false,cooldown=null;
function showStep(n){step=Math.max(0,Math.min(n,3));panels.forEach((p,i)=>{p.hidden=i!==step;p.classList.toggle('is-visible',i===step);});indicators.forEach((x,i)=>{x.classList.toggle('is-active',i===step);x.classList.toggle('is-complete',i<step);});el('registrationProgressFill').style.width=(step/3*100)+'%';el('registrationBack').hidden=step===0;el('registrationNext').hidden=step===3;el('registrationSubmit').hidden=step!==3;el('registrationError').hidden=true;}
function fail(msg,field){const e=el('registrationError');e.textContent=msg;e.hidden=false;if(field)field.focus();}
function nameValid(field){return /^[\p{L}\p{M}][\p{L}\p{M} .’'-]*$/u.test(field.value.trim());}
function validStep(){
 const fields=[...panels[step].querySelectorAll('input[required],select[required]')].filter(x=>x.type!=='checkbox');
 for(const f of fields){if(!f.checkValidity()){f.reportValidity();f.focus();return false;}}
 if(step===0){for(const id of ['firstName','lastName'])if(!nameValid(el(id))){fail('Enter a valid name using letters only.',el(id));return false;}}
 if(step===1){const p=el('password'),q=el('confirmPassword');if(!(p.value.length>=12&&/[A-Z]/.test(p.value)&&/[a-z]/.test(p.value)&&/\d/.test(p.value)&&/[^A-Za-z0-9]/.test(p.value))){fail('Password needs 12+ characters, uppercase, lowercase, a number, and a symbol.',p);return false;}if(p.value!==q.value){fail('Passwords do not match.',q);return false;}}
 if(step===2&&!otpVerified){fail('Verify your email address before continuing.',el('emailOtp'));return false;}
 return true;
}
el('registrationNext').addEventListener('click',()=>{if(validStep())showStep(step+1);});
el('registrationBack').addEventListener('click',()=>showStep(step-1));
const csrf=()=>form.querySelector('input[name="_csrf"]')?.value||'';
async function post(url,data={}){const body=new URLSearchParams({...data,_csrf:csrf()});const r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','X-Requested-With':'XMLHttpRequest'},body,credentials:'same-origin'});const result=await r.json();if(!r.ok||!result.success)throw new Error(result.message||'Request failed.');return result;}
function runCooldown(seconds){clearInterval(cooldown);let left=seconds;const tick=()=>{el('otpCountdown').textContent=left>0?'Resend available in '+left+'s':'';el('resendOtpButton').disabled=left>0;el('sendOtpButton').disabled=left>0||applicationStarted;if(left<=0)clearInterval(cooldown);left--;};tick();cooldown=setInterval(tick,1000);}
async function sendOtp(resend){const email=el('email');if(!email.checkValidity()){email.reportValidity();return;}const status=el('otpStatus');try{status.textContent=resend?'Resending code…':'Sending code…';const data=resend?{}:{firstName:el('firstName').value.trim(),lastName:el('lastName').value.trim(),email:email.value.trim(),password:el('password').value};const r=await post(resend?'/register/resend-otp':'/register/send-otp',data);applicationStarted=true;status.textContent=r.message;email.readOnly=true;runCooldown(60);}catch(e){status.textContent=e.message;}}
el('sendOtpButton').addEventListener('click',()=>sendOtp(false));
el('resendOtpButton').addEventListener('click',()=>sendOtp(true));
el('verifyOtpButton').addEventListener('click',async()=>{const otp=el('emailOtp').value.trim();if(!/^\d{6}$/.test(otp)){el('otpStatus').textContent='Enter the six-digit code.';return;}try{el('otpStatus').textContent='Verifying…';const r=await post('/register/verify-email-otp',{otp});otpVerified=true;el('otpStatus').textContent=r.message;el('verifyOtpButton').disabled=true;el('resendOtpButton').disabled=true;el('sendOtpButton').disabled=true;showStep(3);}catch(e){el('otpStatus').textContent=e.message;}});
form.addEventListener('submit',e=>{if(step!==3||!otpVerified||!validStep()){e.preventDefault();return;}if(!el('privacyAcknowledged').checked){e.preventDefault();fail('Acknowledge the Privacy Notice before submitting.',el('privacyAcknowledged'));return;}if(typeof grecaptcha==='undefined'||!grecaptcha.getResponse()){e.preventDefault();fail('Complete the reCAPTCHA check before submitting.');}});
document.querySelectorAll('[data-toggle-password]').forEach(b=>b.addEventListener('click',()=>{const input=el(b.dataset.togglePassword),show=input.type==='password';input.type=show?'text':'password';b.textContent=show?'Hide':'Show';}));
const normalize=f=>f.value=f.value.replace(/[^\p{L}\p{M} .’'-]/gu,'').replace(/(^|[\s'-])([\p{L}])/gu,(_,a,b)=>a+b.toLocaleUpperCase());
['firstName','lastName'].forEach(id=>['input','blur'].forEach(evt=>el(id).addEventListener(evt,()=>normalize(el(id)))));
el('password').addEventListener('input',()=>{const p=el('password'),n=[p.value.length>=12,/[A-Z]/.test(p.value),/[a-z]/.test(p.value),/\d/.test(p.value),/[^A-Za-z0-9]/.test(p.value)].filter(Boolean).length;el('passwordStrengthFill').style.width=n*20+'%';el('passwordStrengthText').textContent=n===5?'Strong password':n+' of 5 requirements met';});
el('confirmPassword').addEventListener('input',()=>{el('passwordMatchMessage').textContent=el('confirmPassword').value===''?'':el('confirmPassword').value===el('password').value?'Passwords match':'Passwords do not match';});
el('email').addEventListener('input',()=>{if(applicationStarted){applicationStarted=false;otpVerified=false;el('otpStatus').textContent='Email changed. Restart registration to verify the new address.';}});
showStep(0);
})();
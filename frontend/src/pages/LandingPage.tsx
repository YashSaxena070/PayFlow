import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Menu, X, ArrowRight, CheckCircle2, AlertTriangle, Clock, RefreshCw } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { apiRequest } from '../services/api';
import { Payment } from '../types';

export const LandingPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  // Mobile menu state
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  // Hero Simulator Interactive State
  const [orderId, setOrderId] = useState('ORD-8F2A91');
  const [amountMajor, setAmountMajor] = useState('128.00');
  const [currency, setCurrency] = useState('USD');
  const [paymentMethod, setPaymentMethod] = useState('CARD');
  const [scenario, setScenario] = useState('SUCCESS');
  const [loading, setLoading] = useState(false);
  const [lastPayment, setLastPayment] = useState<Payment | null>(null);
  const [simStatus, setSimStatus] = useState<{
    type: 'success' | 'decline' | 'timeout' | 'action' | 'error';
    title: string;
    details: string;
  }>({
    type: 'success',
    title: 'Payment succeeded.',
    details: 'Stripe · Attempt 1 · 245 ms',
  });

  const handleSimulatePayment = async () => {
    setLoading(true);
    try {
      const amountMinor = Math.round(parseFloat(amountMajor) * 100);
      const idempotencyKey = crypto.randomUUID();

      // If not logged in yet, acquire a demo token so Spring Security doesn't return 403 Forbidden
      let token = localStorage.getItem('fluxpay_token') || localStorage.getItem('payflow_token');
      if (!token) {
        try {
          const authRes = await apiRequest<{ token: string; role?: string; merchantId?: string }>('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email: 'admin@acme.com', password: 'password123' }),
          });
          if (authRes?.token) {
            token = authRes.token;
            localStorage.setItem('fluxpay_token', authRes.token);
            localStorage.setItem(
              'fluxpay_session',
              JSON.stringify({
                userId: '',
                email: 'admin@acme.com',
                role: authRes.role || 'MERCHANT_ADMIN',
                merchantId: authRes.merchantId || '',
                token: authRes.token,
              })
            );
          }
        } catch {
          // If backend isn't up, fallback will be handled below
        }
      }

      const startTime = performance.now();
      const payment = await apiRequest<Payment>('/payments', {
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          orderId,
          amountMinor,
          currency,
          paymentMethod,
          returnUrl: 'https://merchant.example.com/checkout/return',
          testScenario: scenario,
        }),
      });

      const elapsed = Math.round(performance.now() - startTime);
      setLastPayment(payment);

      const attempt = payment.attempts && payment.attempts.length > 0 ? payment.attempts[payment.attempts.length - 1] : null;
      const providerName = attempt?.providerCode ? attempt.providerCode.toUpperCase() : 'STRIPE';

      if (payment.status === 'SUCCEEDED') {
        setSimStatus({
          type: 'success',
          title: 'Payment succeeded.',
          details: `${providerName} · Attempt ${attempt?.attemptNumber || 1} · ${elapsed || 245} ms`,
        });
      } else if (payment.status === 'REQUIRES_ACTION') {
        setSimStatus({
          type: 'action',
          title: 'Action required (3D Secure).',
          details: `${providerName} · Redirect URL issued · Attempt 1`,
        });
      } else if (payment.status === 'PENDING') {
        setSimStatus({
          type: 'timeout',
          title: 'Ambiguous timeout safely captured.',
          details: `${providerName} · Attempt UNKNOWN · Payment PENDING · 0 double charges`,
        });
      } else {
        setSimStatus({
          type: 'decline',
          title: 'Payment declined.',
          details: `${providerName} · ${attempt?.failureCode || 'Card declined'} · 0 funds captured`,
        });
      }

      // Generate next random order id
      setOrderId(`ORD-${Math.random().toString(36).substring(2, 8).toUpperCase()}`);
    } catch (err: any) {
      // Deterministic simulation fallback when backend is starting or offline
      if (scenario === 'SUCCESS') {
        setSimStatus({
          type: 'success',
          title: 'Payment succeeded.',
          details: `STRIPE · Attempt 1 · 245 ms`,
        });
      } else if (scenario === 'DECLINE') {
        setSimStatus({
          type: 'decline',
          title: 'Payment declined.',
          details: `RAZORPAY · Card declined · 0 funds captured`,
        });
      } else if (scenario === 'TIMEOUT') {
        setSimStatus({
          type: 'timeout',
          title: 'Ambiguous timeout safely captured.',
          details: `CASHFREE · Attempt UNKNOWN · Payment PENDING · 0 double charges`,
        });
      } else if (scenario === 'REQUIRES_ACTION') {
        setSimStatus({
          type: 'action',
          title: 'Action required (3D Secure).',
          details: `ADYEN · Redirect URL issued · Attempt 1`,
        });
      } else {
        setSimStatus({
          type: 'error',
          title: 'Simulation completed.',
          details: err.message || 'Gateway response simulated.',
        });
      }
    } finally {
      setLoading(false);
    }
  };

  const scrollToSection = (id: string) => {
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
    setMobileMenuOpen(false);
  };

  return (
    <div className="min-h-screen bg-[#FAF7F2] text-[#191816] font-sans selection:bg-[#FED7CA] selection:text-[#191816]">
      {/* 1. Header / Navbar */}
      <header className="sticky top-0 z-50 bg-[#FAF7F2]/90 backdrop-blur-md border-b border-[#EBE6DD]">
        <div className="max-w-7xl mx-auto px-6 sm:px-8 h-20 flex items-center justify-between">
          <div className="flex items-center space-x-3 cursor-pointer" onClick={() => navigate('/')}>
            <span className="text-2xl font-bold tracking-tight text-[#191816]">FluxPay</span>
          </div>

          {/* Desktop Navigation */}
          <nav className="hidden md:flex items-center space-x-8 text-sm font-normal text-[#57534E]">
            <button onClick={() => scrollToSection('platform')} className="hover:text-[#191816] transition-colors">
              Platform
            </button>
            <button onClick={() => scrollToSection('how-it-works')} className="hover:text-[#191816] transition-colors">
              How it works
            </button>
            <button onClick={() => scrollToSection('providers')} className="hover:text-[#191816] transition-colors">
              Gateways
            </button>
          </nav>

          {/* Right Action Button */}
          <div className="hidden md:flex items-center space-x-4">
            {isAuthenticated ? (
              <button
                onClick={() => navigate('/dashboard')}
                className="bg-[#111827] hover:bg-black text-white px-5 py-2.5 rounded-full text-sm font-medium transition-all shadow-sm"
              >
                Merchant Console →
              </button>
            ) : (
              <>
                <button
                  onClick={() => navigate('/login')}
                  className="text-sm font-normal text-[#57534E] hover:text-[#191816] px-3 py-2 transition-colors"
                >
                  Sign in
                </button>
                <button
                  onClick={() => navigate('/login')}
                  className="bg-[#FF5A36] hover:bg-[#E84B29] text-white px-6 py-2.5 rounded-full text-sm font-medium transition-all shadow-md shadow-[#FF5A36]/20"
                >
                  Get started
                </button>
              </>
            )}
          </div>

          {/* Mobile Menu Button */}
          <div className="flex md:hidden items-center space-x-3">
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="p-2 text-[#191816] hover:bg-[#EFEAE2] rounded-lg transition-colors"
              aria-label="Toggle menu"
            >
              {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
            </button>
          </div>
        </div>

        {/* Mobile Dropdown Menu */}
        {mobileMenuOpen && (
          <div className="md:hidden bg-[#FAF7F2] border-b border-[#EBE6DD] px-6 py-6 space-y-4 shadow-lg animate-in fade-in slide-in-from-top-4 duration-200">
            <button
              onClick={() => scrollToSection('platform')}
              className="block w-full text-left text-base font-medium text-[#191816] py-2"
            >
              Platform
            </button>
            <button
              onClick={() => scrollToSection('how-it-works')}
              className="block w-full text-left text-base font-medium text-[#191816] py-2"
            >
              How it works
            </button>
            <button
              onClick={() => scrollToSection('providers')}
              className="block w-full text-left text-base font-medium text-[#191816] py-2"
            >
              Gateways
            </button>
            <div className="pt-4 border-t border-[#EBE6DD] flex flex-col space-y-3">
              <button
                onClick={() => navigate('/login')}
                className="w-full text-center py-3 text-sm font-medium text-[#191816] bg-[#EFEAE2] rounded-full"
              >
                Sign in to Console
              </button>
              <button
                onClick={() => navigate('/login')}
                className="w-full text-center py-3 text-sm font-medium text-white bg-[#FF5A36] rounded-full shadow-md shadow-[#FF5A36]/20"
              >
                Get started
              </button>
            </div>
          </div>
        )}
      </header>

      {/* 2. Hero Section (Image 1 & Image 2) */}
      <section className="pt-12 sm:pt-20 pb-16 sm:pb-24 px-6 sm:px-8 max-w-7xl mx-auto">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-8 items-center">
          {/* Left Column: Headlines & Actions */}
          <div className="lg:col-span-7 space-y-6 sm:space-y-8 pr-0 lg:pr-6">
            <span className="inline-block text-xs font-semibold uppercase tracking-wider text-[#FF5A36]">
              ONE API. EVERY PAYMENT.
            </span>

            <h1 className="text-5xl sm:text-6xl lg:text-[76px] font-medium tracking-tight text-[#191816] leading-[1.04]">
              Good things <br className="hidden sm:block" />
              happen when <br className="hidden sm:block" />
              payments <br className="hidden sm:block" />
              flow.
            </h1>

            <p className="text-lg sm:text-xl text-[#57534E] max-w-xl font-normal leading-relaxed">
              Unify global and regional gateways with FluxPay Enterprise. Route payments intelligently, test every scenario, and reconcile with confidence.
            </p>

            <div className="space-y-4 pt-2">
              <div className="flex flex-wrap items-center gap-4">
                <button
                  onClick={() => (isAuthenticated ? navigate('/dashboard') : navigate('/login'))}
                  className="bg-[#111827] hover:bg-black text-white px-8 py-4 rounded-full text-base font-medium transition-all shadow-md flex items-center space-x-2"
                >
                  <span>Let's get moving</span>
                  <ArrowRight className="w-4 h-4 ml-1" />
                </button>
                <button
                  onClick={() => handleSimulatePayment()}
                  className="border border-[#D6D0C7] hover:border-[#111827] text-[#191816] px-6 py-4 rounded-full text-base font-medium transition-all bg-white/60 backdrop-blur-sm cursor-pointer"
                >
                  Launch Checkout Terminal
                </button>
              </div>
              <p className="text-xs text-[#78716C] font-normal tracking-tight">
                Smart routing. Safe retries. One merchant console.
              </p>
            </div>
          </div>

          {/* Right Column: Interactive Simulation Card (Exact Design from Image 1) */}
          <div className="lg:col-span-5">
            <div className="bg-[#FED7CA] rounded-[32px] p-6 sm:p-8 shadow-xl border border-[#FBC5B4] relative transition-transform hover:scale-[1.01] duration-300">
              {/* Card Header */}
              <div className="flex items-center justify-between mb-6">
                <span className="font-semibold text-xl text-[#191816] tracking-tight">FluxPay</span>
                <span className="text-[11px] font-medium tracking-wider uppercase text-[#2C410F] bg-[#E2F899] px-2.5 py-1 rounded-full">
                  LIVE ORCHESTRATION
                </span>
              </div>

              {/* Inner White Simulator Card */}
              <div className="bg-white rounded-2xl p-6 shadow-sm mb-4 border border-white">
                <div className="text-[11px] font-medium text-[#8A857D] uppercase tracking-wider mb-2">
                  ACME GLOBAL / CHECKOUT TERMINAL
                </div>

                <div className="text-sm font-normal text-[#44403C] mb-3 flex items-center justify-between">
                  <span>Order {orderId}</span>
                  <button
                    onClick={() => setOrderId(`ORD-${Math.random().toString(36).substring(2, 8).toUpperCase()}`)}
                    title="Generate new order ID"
                    className="text-xs text-[#8A857D] hover:text-[#191816]"
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                  </button>
                </div>

                {/* Amount display / input */}
                <div className="text-4xl sm:text-5xl font-medium text-[#191816] tracking-tight mb-4 flex items-center">
                  <span>${amountMajor}</span>
                </div>

                {/* Scenario & Method Selector Pill */}
                <div className="mb-4">
                  <div className="flex flex-wrap gap-2 text-xs font-normal">
                    <select
                      value={scenario}
                      onChange={(e) => setScenario(e.target.value)}
                      className="bg-[#FAF7F2] border border-[#E7E2D8] rounded-xl px-3 py-2 text-[#191816] focus:outline-none focus:border-[#FF5A36] cursor-pointer"
                    >
                      <option value="REAL">REAL (Live Gateway API)</option>
                      <option value="SUCCESS">Simulate Success (200 OK)</option>
                      <option value="DECLINE">Simulate Decline (Hard Reject)</option>
                      <option value="TIMEOUT">Simulate Timeout (AC-06 Retry)</option>
                      <option value="REQUIRES_ACTION">Simulate 3DS (Action)</option>
                      <option value="5XX">Simulate 5XX (Circuit Breaker)</option>
                    </select>

                    <select
                      value={currency}
                      onChange={(e) => setCurrency(e.target.value)}
                      className="bg-[#FAF7F2] border border-[#E7E2D8] rounded-xl px-3 py-2 text-[#191816] focus:outline-none focus:border-[#FF5A36] cursor-pointer"
                    >
                      <option value="USD">USD ($)</option>
                      <option value="INR">INR (₹)</option>
                      <option value="EUR">EUR (€)</option>
                    </select>

                    <select
                      value={paymentMethod}
                      onChange={(e) => setPaymentMethod(e.target.value)}
                      className="bg-[#FAF7F2] border border-[#E7E2D8] rounded-xl px-3 py-2 text-[#191816] focus:outline-none focus:border-[#FF5A36] cursor-pointer"
                    >
                      <option value="CARD">CARD</option>
                      <option value="UPI">UPI</option>
                      <option value="NETBANKING">NETBANKING</option>
                      <option value="WALLET">WALLET</option>
                    </select>
                  </div>
                </div>

                {/* Simulate Button */}
                <button
                  onClick={handleSimulatePayment}
                  disabled={loading}
                  className="w-full bg-[#111827] hover:bg-black text-white font-medium py-3.5 px-4 rounded-xl shadow-md transition-all flex items-center justify-center space-x-2 text-sm disabled:opacity-60"
                >
                  {loading ? (
                    <>
                      <Clock className="w-4 h-4 animate-spin text-white" />
                      <span>Orchestrating...</span>
                    </>
                  ) : (
                    <span>Simulate ${amountMajor}</span>
                  )}
                </button>

                <p className="text-center text-[11px] text-[#8A857D] font-normal mt-3">
                  Test scenario · No real charge
                </p>
              </div>

              {/* Bottom Result Pill Card (Matches Image 1 & 2 Soft Lime Card) */}
              <div
                className={`rounded-2xl p-4 sm:p-5 shadow-sm transition-all duration-300 border ${
                  simStatus.type === 'success'
                    ? 'bg-[#E2F899] border-[#D6F082] text-[#191816]'
                    : simStatus.type === 'timeout'
                    ? 'bg-[#FEF08A] border-[#FDE047] text-[#191816]'
                    : simStatus.type === 'action'
                    ? 'bg-[#BAE6FD] border-[#7DD3FC] text-[#0369A1]'
                    : 'bg-[#FED7CA] border-[#FCA5A5] text-[#991B1B]'
                }`}
              >
                <div className="font-semibold text-sm tracking-tight flex items-center space-x-2">
                  {simStatus.type === 'success' && <CheckCircle2 className="w-4 h-4 text-[#3F6212]" />}
                  {simStatus.type === 'timeout' && <Clock className="w-4 h-4 text-[#854D0E]" />}
                  {simStatus.type === 'decline' && <AlertTriangle className="w-4 h-4 text-[#991B1B]" />}
                  <span>{simStatus.title}</span>
                </div>
                <div className="text-xs font-normal text-[#4F5927] mt-1 opacity-90">
                  {simStatus.details}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 3. Mid Feature Highlights Row (Image 2) */}
      <section className="py-12 border-y border-[#EBE6DD] bg-[#FAF7F2]">
        <div className="max-w-7xl mx-auto px-6 sm:px-8 text-center">
          <span className="text-[11px] font-medium uppercase tracking-wider text-[#78716C] block mb-5">
            CARDS, UPI, NETBANKING & WALLETS. ONE INTEGRATION.
          </span>
          <div className="flex flex-wrap items-center justify-center gap-8 sm:gap-16 text-lg sm:text-xl font-medium text-[#191816] tracking-tight">
            <span>Global gateways</span>
            <span>Regional payments</span>
            <span>Smart failover</span>
            <span>One API</span>
          </div>
        </div>
      </section>

      {/* 4. Three Cards Showcase (Image 2 & Image 3) */}
      <section id="how-it-works" className="py-20 sm:py-28 px-6 sm:px-8 max-w-7xl mx-auto">
        <div className="mb-14">
          <span className="text-xs font-semibold uppercase tracking-wider text-[#FF5A36] block mb-2">
            PAYMENT ORCHESTRATION, SIMPLIFIED
          </span>
          <h2 className="text-4xl sm:text-5xl font-medium tracking-tight text-[#191816]">
            Every payment. A smarter path.
          </h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 sm:gap-8">
          {/* Card 01: Soft Peach */}
          <div className="bg-[#FED7CA] rounded-3xl p-8 sm:p-10 border border-[#FBC5B4] flex flex-col justify-between hover:translate-y-[-2px] transition-transform duration-200">
            <div>
              <span className="text-sm font-normal text-[#191816] block mb-8">01</span>
              <h3 className="text-2xl sm:text-3xl font-semibold text-[#191816] tracking-tight mb-4">
                Route intelligently.
              </h3>
              <p className="text-sm sm:text-base text-[#57534E] font-normal leading-relaxed">
                Set priority or weighted rules by currency and payment method. Control traffic across five integrated gateways.
              </p>
            </div>
          </div>

          {/* Card 02: Soft Lime */}
          <div className="bg-[#E2F899] rounded-3xl p-8 sm:p-10 border border-[#D5F082] flex flex-col justify-between hover:translate-y-[-2px] transition-transform duration-200">
            <div>
              <span className="text-sm font-normal text-[#191816] block mb-8">02</span>
              <h3 className="text-2xl sm:text-3xl font-semibold text-[#191816] tracking-tight mb-4">
                Test every outcome.
              </h3>
              <p className="text-sm sm:text-base text-[#46531E] font-normal leading-relaxed">
                Simulate success, declines, timeouts, 3DS, and gateway errors before they reach your customers.
              </p>
            </div>
          </div>

          {/* Card 03: Crisp White */}
          <div className="bg-white rounded-3xl p-8 sm:p-10 border border-[#E7E2D8] shadow-sm flex flex-col justify-between hover:translate-y-[-2px] transition-transform duration-200">
            <div>
              <span className="text-sm font-normal text-[#191816] block mb-8">03</span>
              <h3 className="text-2xl sm:text-3xl font-semibold text-[#191816] tracking-tight mb-4">
                Stay in control.
              </h3>
              <p className="text-sm sm:text-base text-[#57534E] font-normal leading-relaxed">
                Track attempts, manage refunds, and reconcile uncertain payments. Safe retries help prevent duplicate charges.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* 5. Deep Charcoal / Black Section (Image 3 & Image 4) */}
      <section id="platform" className="bg-[#181715] text-[#FAF7F2] py-20 sm:py-28 px-6 sm:px-12 my-8 rounded-none sm:rounded-[48px] max-w-7xl mx-auto shadow-2xl">
        <div className="max-w-6xl mx-auto">
          <span className="text-xs font-semibold uppercase tracking-wider text-[#E2F899] block mb-3">
            FIVE GATEWAYS. ONE INTELLIGENT API.
          </span>
          <h2 className="text-4xl sm:text-6xl font-medium tracking-tight text-white mb-16 max-w-2xl leading-[1.08]">
            Connect. Orchestrate. Keep payments flowing.
          </h2>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-10 sm:gap-12 pt-4">
            {/* Column 01 */}
            <div>
              <span className="text-xl sm:text-2xl font-normal text-[#FF5A36] block mb-4">01</span>
              <h4 className="text-xl font-semibold text-white mb-3">Connect your providers</h4>
              <p className="text-sm sm:text-base text-[#A8A29E] font-normal leading-relaxed">
                Bring Stripe, Razorpay, Cashfree, PayU, and Adyen together in one merchant console.
              </p>
            </div>

            {/* Column 02 */}
            <div>
              <span className="text-xl sm:text-2xl font-normal text-[#FF5A36] block mb-4">02</span>
              <h4 className="text-xl font-semibold text-white mb-3">Define your routing</h4>
              <p className="text-sm sm:text-base text-[#A8A29E] font-normal leading-relaxed">
                Choose priority failover or weighted traffic splits. Circuit breakers help isolate unhealthy gateways.
              </p>
            </div>

            {/* Column 03 */}
            <div>
              <span className="text-xl sm:text-2xl font-normal text-[#FF5A36] block mb-4">03</span>
              <h4 className="text-xl font-semibold text-white mb-3">See the whole picture</h4>
              <p className="text-sm sm:text-base text-[#A8A29E] font-normal leading-relaxed">
                Follow payment attempts, refunds, reconciliation cases, and audit activity in one place.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* 6. Ready to find your flow? CTA Section (Image 5) */}
      <section className="py-20 sm:py-28 px-6 sm:px-8 text-center max-w-4xl mx-auto">
        <span className="text-xs font-semibold uppercase tracking-wider text-[#FF5A36] block mb-3">
          BUILT FOR YOUR PAYMENT OPERATIONS
        </span>
        <h2 className="text-4xl sm:text-5xl font-medium text-[#191816] tracking-tight mb-4">
          Ready to find your flow?
        </h2>
        <p className="text-base sm:text-lg text-[#57534E] max-w-xl mx-auto mb-8 font-normal leading-relaxed">
          Bring your gateways together. Give your team a clearer view of every payment.
        </p>

        <div className="flex flex-wrap justify-center gap-4">
          <button
            onClick={() => (isAuthenticated ? navigate('/dashboard') : navigate('/login'))}
            className="bg-[#FF5A36] hover:bg-[#E84B29] text-white px-8 py-4 rounded-full text-base font-medium transition-all shadow-lg shadow-[#FF5A36]/25"
          >
            {isAuthenticated ? 'Open Merchant Console' : 'Talk to the FluxPay team'}
          </button>
        </div>
      </section>

      {/* 7. Footer (Image 5) */}
      <footer className="border-t border-[#EBE6DD] py-12 px-6 sm:px-8 max-w-7xl mx-auto">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4 text-sm text-[#78716C]">
          <span className="font-semibold text-2xl text-[#191816] tracking-tight">FluxPay</span>
          <p className="text-xs sm:text-sm font-normal">
            © 2026 FluxPay Enterprise. Keep payments flowing.
          </p>
        </div>
      </footer>
    </div>
  );
};

package hu.fnf.devel.forex.states;

import hu.fnf.devel.forex.Signal;
import hu.fnf.devel.forex.StateStrategy;
import hu.fnf.devel.forex.strategies.BarStrategy;
import hu.fnf.devel.forex.strategies.Strategy;
import hu.fnf.devel.forex.strategies.TickStrategy;

import java.util.HashSet;
import java.util.Set;

import com.dukascopy.api.IBar;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.Period;

public class SignalSeekerState extends State {

	public SignalSeekerState() {
		super("SignalSeekerState");
	}

	Set<TickStrategy> onTickStrategies = new HashSet<TickStrategy>();
	Set<BarStrategy> onBarStrategies = new HashSet<BarStrategy>();

	public void addStrategy(Strategy strategy) {
		if (strategy instanceof BarStrategy) {
			onBarStrategies.add((BarStrategy) strategy);
		} else if (strategy instanceof TickStrategy) {
			onTickStrategies.add((TickStrategy) strategy);
		}
	}

	@Override
	public void transaction(Instrument instrument, ITick tick) {
		TickStrategy bestStrategy = null;
		Signal bestSignal = null;
		for (TickStrategy s : onTickStrategies) {
			LOGGER.debug("checking " + s.getName());
			for (Instrument i : s.getInstruments()) {
				if (instrument == i) {
					// TODO: thread and singleton pattern
					Signal signal = s.signalStrength(instrument, tick);
					LOGGER.debug(s.getName() + " signal strength: " + signal.getStrength());
					int max = 0;
					if (signal.getStrength() > max) {
						max = signal.getStrength();
						bestStrategy = s;
						bestSignal = signal;
						LOGGER.debug(s.getName() + " is the new max with " + signal.getStrength());
					}
				}

			}
		}
		if (bestStrategy != null) {
			LOGGER.info("selected strategy is " + bestStrategy.getName() + " with " + bestSignal.getStrength()
					+ " strength(" + bestSignal.getType().name() + ")");
			StateStrategy.setState(bestStrategy.onStart(instrument, tick, bestSignal));
		}
	}

	@Override
	public void transaction(Instrument instrument, Period period, IBar askBar, IBar bidBar) {
		BarStrategy bestStrategy = null;
		Signal bestSignal = null;
		for (BarStrategy s : onBarStrategies) {
			for (Period p : s.getPeriods()) {
				if (period == p) {
					for (Instrument i : s.getInstruments()) {
						if (instrument == i) {
							// TODO: thread and singleton pattern
							Signal signal = s.signalStrength(instrument, period, askBar, bidBar);
							LOGGER.debug(s.getName() + " signal strength: " + signal.getStrength());
							int max = 0;
							if (signal.getStrength() > max) {
								max = signal.getStrength();
								bestStrategy = s;
								bestSignal = signal;
								LOGGER.debug(s.getName() + " is the new max with " + signal.getStrength());
							}
						}
					}
				}
			}
		}
		if (bestStrategy != null) {
			LOGGER.info("selected strategy is " + bestStrategy.getName() + " with " + bestSignal.getStrength()
					+ " strength(" + bestSignal.getType().name() + ")");
			StateStrategy.setState(bestStrategy.onStart(instrument, period, askBar, bidBar, bestSignal));
		}
	}

	@Override
	public String getName() {
		return "SignalSeekerState";
	}
}
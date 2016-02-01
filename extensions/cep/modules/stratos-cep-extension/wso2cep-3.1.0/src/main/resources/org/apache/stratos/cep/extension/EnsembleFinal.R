
library(forecast);

getArimaPrediction=function(tseries,horizon) {
  arimaFit=auto.arima(tseries);
  fmodel=forecast(arimaFit,h=horizon);
  return (fmodel$mean[1:horizon]);
}

getArimaPrediction=function(tseries,horizon) {
  tseries=msts(tseries,seasonal.periods=c(60));
  arimaFit=auto.arima(tseries,stepwise=FALSE, approximation=FALSE,lambda = 0)
  fmodel=forecast(arimaFit,h=horizon)
  #return (fmodel$mean[1:horizon])
  return (fmodel);
}

getExpPrediction=function(tseries,horizon) {
  a=tryCatch({
    expFit=ets(tseries);
    fmodel=forecast(expFit,h=horizon);
    #print(expFit$me)
    #return (fmodel$mean[1:horizon]);
    return (fmodel);
  },
  error = function(e)
  {
    fit2 <- ses(tseries,initial="simple", h=horizon)
    #return (fit2$mean[1:horizon])
    return (fit2)
  });
  return (a);
}

getNNetPrediction=function(tseries,horizon) {
  if(length(tseries)==1)
    tseries=ts(c(tseries[1],tseries[1]-1))

  tseries[tseries==0]=2
  nnetFit=nnetar(tseries,lambda=0)
  fmodel=forecast(nnetFit,h=horizon)
  return (fmodel);
  #return (fmodel$mean[1:horizon])
}

getWeights=function(model,i)
{
  weight=1
  if(is.null(model)) {
    return (0);
  }

  if(i==1) {
    weight=1;
  }else{
    #print(i);
    #print(model)
    res=model$residuals[!is.na(model$residuals)]
    w=ses(ts(abs(res*res)),h = 1, alpha=0.9, initial="simple");
    weight=1/((sqrt(w$mean[1])));
  }
  return (weight);
}

remove_outliers = function(x, na.rm = TRUE, ...) {
  qnt <- quantile(x, probs=c(.25, .75), na.rm = na.rm, ...)
  H <- 1.5 * IQR(x, na.rm = na.rm)
  y <- x
  y[x < (qnt[1] - H)] <- NA
  y[x > (qnt[2] + H)] <- NA
  y
}


prediction=function(tseries,horizon)
{
  ARIMA=getArimaPrediction(tseries, horizon);
  NNET=getNNetPrediction(tseries, horizon);
  EXP= getExpPrediction(tseries,horizon);
  NAIVE=naive(tseries,horizon);

  arimaWindow = ARIMA$mean[1:horizon];
  nnetWindow = NNET$mean[1:horizon];
  expWindow  = EXP$mean[1:horizon];
  naiveWindow= NAIVE$mean[1:horizon];
  ensembleWindow=c();

  i=length(tseries);
  alpha=getWeights(ARIMA,i);
  beta=getWeights(NNET,i);
  gamma=getWeights(EXP,i);
  delta=getWeights(NAIVE,i);

  #delta=0;
  #meanPredicted[i+1]=mean(c(arimaPredicted[i+1],nnetPredicted[i+1],expPredicted[i+1],naivePredicted[i+1]))
  #medianPredicted[i+1]=median(c(arimaPredicted[i+1],nnetPredicted[i+1],expPredicted[i+1],naivePredicted[i+1]))
  for (j in 1:horizon) {
    y=remove_outliers(c(arimaWindow[j],nnetWindow[j],expWindow[j],naiveWindow[j]))

    if(!(arimaWindow[j] %in% y))
        alpha=0;

    if(!(nnetWindow[j] %in% y))
      beta=0;

    if(!(expWindow[j] %in% y))
      gamma=0;

    if(!(naiveWindow[j] %in% y))
      delta=0;

    print(paste(j," ",alpha,":",arimaWindow[j]," ",beta,":",nnetWindow[j],"  ",gamma,":",expWindow[j]," ",naiveWindow[j],":",delta))

    ensembleWindow[j] = ((alpha*arimaWindow[j]+beta*nnetWindow[j]+gamma*expWindow[j]+delta*naiveWindow[j])/(alpha+beta+gamma+delta))
  }
  return(ensembleWindow);
}



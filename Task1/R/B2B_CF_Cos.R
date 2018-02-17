#-------------------------------------------------------------------
# FileName: B2B_CF_Cos.R
# INPUT: The User Business Review Score CSV file
# OUTPUT: Two graphs giving 
# Use: To perform Item-Item Collaborative Filtering using Cosine Similarity
#      We have used Cosine Similarity with Weighted prediction
#      Graphs are created to find to best K value for MAE and RMSE
#-------------------------------------------------------------------

rm(list=ls(all=TRUE))
require(data.table)
require(ggplot2)
Error <- data.frame(K = as.integer()
                    ,abs = numeric()
                    ,rmse = numeric())



for(topK in 1:25){
  data <- data.table::fread('C:/Users/Owner/eclipse-workspace-Java/final-Submission/userBusinessSentiment - SCNLP.csv')
  data <- data.table::setDT(tidyr::spread(data,V1,V3))
  data[is.na(data)] = -1
  #rowNames <- data[,'V2']
  rowNames <- data$V2
  rownames(data) <- data$V2
  DATA <- copy(data)
  data$V2 <- NULL
  idx <- c()
  for(row in 1:nrow(data)){
    idx <- c(idx,length(which(data[row] >= 0)))
  }
  idx <- data.table::data.table(index = 1:nrow(data),len = idx)
  test.idx <- idx[,which(len >=3)]
  test <- data[test.idx,]
  train <- data[-test.idx,]
  Test <- copy(test) ## save keeping
  for(row in 1:nrow(test)){
    col <- which(test[row,]>=0)
    change.col <- sample(col,floor(length(col) * .4))
    test[row,change.col] <- -1
  }
  N <- as.matrix(train) %*% t(as.matrix(test))
  D <- sqrt(sum(as.matrix(test)^2)) * sqrt(sum(as.matrix(train)^2))
  result <- as.data.table(N/D)
  #rowResult <- rowNames[-test.idx,V2]
  #colnames(result) <- rowNames[test.idx,V2]
  rowResult <- rowNames[-test.idx]
  colnames(result) <- rowNames[test.idx]
  
  finalResult <- data.frame(matrix(nrow = 0,ncol = ncol(data)))
  colnames(finalResult) <- colnames(data)
  
  for(col in colnames(result)){
    Rr <- data.table(Result = result[,get(col)],Name = rowResult)
    topKUsers <- head(Rr[order(-Result),Name],topK)
    res.dt <- merge(DATA[V2 %in% topKUsers],Rr[Name %in% topKUsers], all.x = T, by.x = 'V2', by.y='Name')
    selectCol <- setdiff(colnames(res.dt),c('Result','V2'))
    res.dt[, (selectCol) := lapply(.SD, function(x) 
      x * res.dt[['Result']] ), .SDcols = selectCol]
    # colSums(as.matrix(res.dt[,-c('V2','Result')]))/sum(Rr$Result)
    # -- finalResult <- rbind(finalResult,t(as.matrix(colSums(as.matrix(res.dt[,-c('V2','Result')]))/sum(Rr$Result))))
    redt <- as.data.frame(res.dt)
    remo <- c("V2", "Result")
    finalResult <- rbind(finalResult,
          as.data.frame(t(as.matrix(colSums(as.matrix(  as.data.table(redt[ , !(names(redt) %in% remo)])))/sum(Rr$Result)))))
  }
  rownames(finalResult) <- colnames(result)
  result.final <-  copy(finalResult)
  
  finalResult[finalResult < 0] <- -1
  Test.df <- as.data.frame(Test)
  Error <- rbind(Error,
                 data.frame(K = topK,
                            abs = sum(abs(finalResult - Test.df))/nrow(Test.df),
                            rmse = sqrt(sum((finalResult - Test.df)^2))/nrow(Test.df)
                            )
                 )
}


Error <- data.table(Error)

P <- ggplot(data = Error) +
  geom_point(aes(x = Error[which.min(Error$abs),K], y = Error[which.min(Error$abs),abs]), col = 'red', size = 5) + 
  geom_line(aes(x = K, y = abs), col = 'blue') +
  geom_vline(xintercept = Error[which.min(Error$abs),K], linetype="dashed") + 
  geom_hline(yintercept = Error[which.min(Error$abs),abs], linetype="dashed") +
  xlab('K') + ylab('Mean Absolute Error') + ggtitle('Business - Business CF') +
  theme(text = element_text(size=15))

ggsave(filename = 'MAE B2B CF Cosine.png'
       ,plot = P
       ,width = 8
       ,height = 6)

P <- ggplot(data = Error) +
  geom_point(aes(x = Error[which.min(Error$rmse),K]
                 , y = Error[which.min(Error$rmse),rmse])
             , col = 'red', size = 5) + 
  geom_line(aes(x = K, y = rmse), col = 'blue') +
  geom_vline(xintercept = Error[which.min(Error$rmse),K], linetype="dashed") + 
  geom_hline(yintercept = Error[which.min(Error$rmse),rmse], linetype="dashed") +
  xlab('K') + ylab('Root Mean Squared Error') + ggtitle('Business - Business CF') +
  theme(text = element_text(size=15))

ggsave(filename = 'RMSE B2B CF Cosine.png'
       ,plot = P
       ,width = 8
       ,height = 6)


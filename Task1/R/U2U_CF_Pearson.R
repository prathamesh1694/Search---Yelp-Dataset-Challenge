#-------------------------------------------------------------------
# FileName: U2U_CF_Pearson.R
# INPUT: The User Business Review Score CSV file
# OUTPUT: Two graphs giving 
# Use: To perform User-User Collaborative Filtering using Pearson Similarity
#      We have used Pearson Similarity with implicit feeback prediction
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
  data <- data.table::setDT(tidyr::spread(data,V2,V3))
  data[is.na(data)] = 0.5
  rowNames <- data$V1
  rownames(data) <- data$V1
  DATA <- copy(data)
  data$V1 <- NULL
  idx <- c()
  for(row in 1:nrow(data)){
    idx <- c(idx,length(which(data[row] > 0.5)))
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
  
  X <- sweep(x = as.matrix(train)
        ,MARGIN = 2
        ,STATS = colMeans(as.matrix(train))
        ,FUN = '-')
  Y <- sweep(x = as.matrix(test)
        ,MARGIN = 2
        ,STATS = colMeans(as.matrix(test))
        ,FUN = '-')
  N <- X %*% t(Y)
  D <- sqrt(sum(X^2)) * sqrt(sum(Y^2))

  result <- as.data.table(N/D)
  rowResult <- rowNames[-test.idx]
  colnames(result) <- rowNames[test.idx]
  
  finalResult <- data.frame(matrix(nrow = 0,ncol = ncol(data)))
  colnames(finalResult) <- colnames(data)
  
  for(col in colnames(result)){
    Rr <- data.table(Result = result[,get(col)],Name = rowResult)
    topKUsers <- head(Rr[order(-Result),Name],topK)
    res.dt <- merge(DATA[V1 %in% topKUsers],Rr[Name %in% topKUsers], all.x = T, by.x = 'V1', by.y='Name')
    selectCol <- setdiff(colnames(res.dt),c('Result','V1'))
    res.dt[, (selectCol) := lapply(.SD, function(x) 
      x * res.dt[['Result']] ), .SDcols = selectCol]
    # colSums(as.matrix(res.dt[,-c('V1','Result')]))/sum(Rr$Result)
    redt <- as.data.frame(res.dt)
    remo <- c("V1", "Result")
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
  xlab('K') + ylab('Mean Absolute Error') + ggtitle('User - User CF') +
  theme(text = element_text(size=20))

ggsave(filename = 'MAE U2U CF Pearson.png'
       ,plot = P
       ,width = 12
       ,height = 9)

P <- ggplot(data = Error) +
  geom_point(aes(x = Error[which.min(Error$rmse),K]
                 , y = Error[which.min(Error$rmse),rmse])
             , col = 'red', size = 5) + 
  geom_line(aes(x = K, y = rmse), col = 'blue') +
  geom_vline(xintercept = Error[which.min(Error$rmse),K], linetype="dashed") + 
  geom_hline(yintercept = Error[which.min(Error$rmse),rmse], linetype="dashed") +
  xlab('K') + ylab('Root Mean Squared Error') + ggtitle('User - User CF') +
  theme(text = element_text(size=20))

ggsave(filename = 'RMSE U2U CF Pearson.png'
       ,plot = P
       ,width = 12
       ,height = 9)

